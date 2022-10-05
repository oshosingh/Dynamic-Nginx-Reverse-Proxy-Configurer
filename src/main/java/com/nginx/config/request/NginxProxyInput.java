package com.nginx.config.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NginxProxyInput {
	private String proxyUrl;
	private String proxyPath;
}
