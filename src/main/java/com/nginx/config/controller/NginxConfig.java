package com.nginx.config.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nginx.config.request.NginxProxyInput;
import com.nginx.config.service.NginxConfigService;

@RestController	
@RequestMapping("/nginx")
public class NginxConfig {
	
	@Autowired
	private NginxConfigService nginxService;
	
	@GetMapping("/get/config")
	String getNginxConfig() {
		return nginxService.getNginxConfig();
	}
	
	@PostMapping("/add/location")
	String addLocation(@ModelAttribute NginxProxyInput nginxProxyInput) {
		return nginxService.addLocation(nginxProxyInput);
	}

}
