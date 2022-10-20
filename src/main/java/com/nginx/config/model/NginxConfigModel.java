package com.nginx.config.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="nginx_location")
@Getter
@Setter
public class NginxConfigModel {
	
	@Id
	private Long locationId;
	private String sandboxCode;
	private String proxyUrl;

}
