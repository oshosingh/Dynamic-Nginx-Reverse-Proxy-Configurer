package com.nginx.config.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NginxProxyInput {
	private String sandboxCode;
	private String proxyUrl;
	private boolean rewritePrefix;
}
