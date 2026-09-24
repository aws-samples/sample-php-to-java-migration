# PHP baseline for parity testing: the official Symfony Demo application under
# migration, served on SQLite with the demo fixtures loaded.
#
# This image is built from the read-only PHP source of truth
# (symfony-demo-migration/source-php) so the parity harness diffs the Java port
# against the exact code being migrated — not a pre-built binary. It runs on the
# built-in PHP web server with the default SQLite database, which needs no
# external DB service.
#
# Build context is the repository's source-php tree (see compose-parity.yml,
# which sets the context to ../../../../../source-php).
#
# Base images are pulled from Amazon ECR Public (public.ecr.aws — the ECR mirror
# of the Docker Hub official images). ECR Public allows anonymous pulls with no
# registry authentication and avoids Docker Hub rate limits, and many enterprise
# security policies require container images to come from an ECR-hosted source.
# The references are literal (not ARG-parameterized) so static security scanners
# can resolve them.

# Named stage so /usr/bin/composer can be copied into the PHP image below via
# COPY --from=composer_image.
FROM public.ecr.aws/docker/library/composer:2 AS composer_image

FROM public.ecr.aws/docker/library/php:8.4-cli

# System libraries + PHP extensions the Symfony Demo requires
# (pdo_sqlite for the default DB, intl for i18n, zip for composer installs).
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        git unzip libicu-dev libsqlite3-dev libzip-dev \
    && docker-php-ext-install pdo_sqlite intl zip \
    && rm -rf /var/lib/apt/lists/*

# Composer (copied from the official composer image).
COPY --from=composer_image /usr/bin/composer /usr/bin/composer

WORKDIR /app

# App environment: force prod-like dev demo settings, SQLite default DB.
ENV APP_ENV=dev \
    APP_DEBUG=0 \
    DATABASE_URL="sqlite:///%kernel.project_dir%/data/database.sqlite" \
    MAILER_DSN=null://null

# Install dependencies (skip the interactive/asset auto-scripts during image build).
COPY composer.json composer.lock symfony.lock ./
RUN composer install --no-interaction --no-scripts --prefer-dist --no-progress

# Bring in the rest of the application source.
COPY . .

# Prepare the SQLite database, schema, and demo fixtures so the baseline is seeded
# identically to the Java port. Best-effort migrate then fixtures load.
RUN mkdir -p data var \
    && php bin/console doctrine:database:create --if-not-exists -n || true \
    && (php bin/console doctrine:migrations:migrate -n \
        || php bin/console doctrine:schema:create -n) \
    && php bin/console doctrine:fixtures:load -n \
    && chmod -R 0777 data var

# Run as a non-root user rather than the image's default root, per container
# security best practice. www-data (uid 33) already exists in the base image
# and owns nothing else we need to chown, since data/var were just made
# world-writable above for the SQLite/cache directories.
USER www-data

EXPOSE 8000

# Serve the app from the public/ document root on the built-in web server.
CMD ["php", "-S", "0.0.0.0:8000", "-t", "public"]
