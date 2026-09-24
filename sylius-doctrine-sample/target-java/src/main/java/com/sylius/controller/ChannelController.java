package com.sylius.controller;

import com.sylius.entity.Channel;
import com.sylius.repository.ChannelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelRepository channelRepository;

    public ChannelController(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @GetMapping
    public List<Channel> list() {
        return channelRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Channel> get(@PathVariable Long id) {
        return channelRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
