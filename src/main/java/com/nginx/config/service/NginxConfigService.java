package com.nginx.config.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nginx.config.model.NginxConfigModel;
import com.nginx.config.repo.NginxLocationRepo;
import com.nginx.config.request.NginxProxyInput;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NginxConfigService {

	@Autowired
	private NginxLocationRepo nginxLocationRepo;

	@Value("${nginx.conf.path}")
	private String nginxWriteConfPath;

	private String config;

	public String getNginxConfig() {
		List<NginxConfigModel> lis = nginxLocationRepo.findAll();

		List<List<NginxConfigModel>> partition = new ArrayList<>();
		int partitionSize = 4;

		for (int i = 0; i < lis.size(); i += partitionSize) {
			partition.add(lis.subList(i, i + Math.min(partitionSize, lis.size() - i)));
		}

		config = "server { \n" + "	 listen 80; \n" + "\t listen [::]:80; \n" + "	 server_name _; \n\n";

		genReverseProxyConfig(partition, 3);

		config += "}";

		boolean isWriteSuccess = writeToFile();

		if (isWriteSuccess == false) {
			return "Write Failed";
		}

		try {
			boolean reloadedNginx = reloadNginx();
			if (reloadedNginx) {
				log.info("Updated nginx with latest config");
			} else {
				log.info("Skipped Nginx reload");
			}
		} catch (Exception e) {
			log.error("Exception while reloading nginx : {}", e.getMessage());
			log.info("Skipped Nginx reload");
		}

		log.info("Exiting get nginx config method");

		return config;
	}

	private boolean reloadNginx() throws Exception {
		String command = "sudo nginx -t";
		Process proc = Runtime.getRuntime().exec(command);

		try {
			String stdout = new BufferedReader(new InputStreamReader(proc.getInputStream())).lines()
					.collect(Collectors.joining("\n"));

			String stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream())).lines()
					.collect(Collectors.joining("\n"));

			log.info("Output of the command {} stdout : {}", command, stdout);
			log.info("Output of the command {} stderr : {}", command, stderr);
			
			// Check if syntax of nginx config file is correct
			if(!stdout.contains("syntax is ok") && !stderr.contains("syntax is ok")) {
				return false;
			}


		} catch (Exception e) {
			log.error("Exception while reading proc object : {}", e.getMessage());
		}

		command = "sudo service nginx reload";
		proc = Runtime.getRuntime().exec(command);

		try {
			String stdout = new BufferedReader(new InputStreamReader(proc.getInputStream())).lines()
					.collect(Collectors.joining("\n"));

			String stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream())).lines()
					.collect(Collectors.joining("\n"));

			log.info("Output of the command {} stdout : {}", command, stdout);
			log.info("Output of the command {} stderr : {}", command, stderr);

		} catch (Exception e) {
			log.error("Exception while reading proc object : {}", e.getMessage());
		}

		log.info("Nginx reloaded");
		return true;
	}

	private boolean writeToFile() {
		Path path = Paths.get(nginxWriteConfPath);
		try {
			Files.writeString(path, config, StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			log.error("Error while saving config : {} classname : {}", e.getMessage(), e.getClass().getName());
			return false;
		}

	}

	private void genReverseProxyConfig(List<List<NginxConfigModel>> partition, int threadCount) {
		ExecutorService executors = Executors.newFixedThreadPool(threadCount);

		for (List<NginxConfigModel> set : partition) {
			executors.execute(() -> {
				for (NginxConfigModel nginxConfigModel : set) {
					// System.out.println("Thread name : "+ Thread.currentThread().getName() + " id
					// val : " + val);
					addLocationBlocks(getLocationBlock(nginxConfigModel));
				}
			});
		}

		// Shutdown the Executor, so it cannot receive more threads
		executors.shutdown();

		// Causes the main thread to wait until all executor threads are done executing
		// their tasks
		try {
			executors.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private synchronized void addLocationBlocks(String locationBlock) {
		// System.out.println("Thread working : "+ Thread.currentThread().getName());
		config += locationBlock;
		// System.out.println("Thread Done : "+ Thread.currentThread().getName());
		// System.out.println(config);
	}

	private String getLocationBlock(NginxConfigModel nginxConfigModel) {
		String proxyHeaders = getProxyPassHeaders();
		String rewrite = "	\t rewrite ^/"+nginxConfigModel.getSandboxCode()+"(.*) $1 break; \n";

		return "\t location /" + nginxConfigModel.getSandboxCode() + "/ { \n" + rewrite + "	\t proxy_pass "
				+ nginxConfigModel.getProxyUrl() + ";" +  proxyHeaders   + "\n" + "\t } \n \n";
	}

	private String getProxyPassHeaders() {
		return "	proxy_set_header Host $host;\r\n"
				+ "      proxy_set_header Upgrade $http_upgrade;\r\n"
				+ "      proxy_set_header Connection upgrade;";
	}

	public String addLocation(NginxProxyInput nginxProxyInput) {
		Long locationId = nginxLocationRepo.maxId();
		locationId = locationId == null ? 1 : locationId + 1;

		NginxConfigModel model = new NginxConfigModel();
		model.setLocationId(locationId);
		model.setSandboxCode(nginxProxyInput.getSandboxCode());
		model.setProxyUrl(nginxProxyInput.getProxyUrl());

		nginxLocationRepo.saveAndFlush(model);
		log.info("Added new location in nginx config");
		
		log.info("Updating nginx config and reloading nginx server");
		
		// Update the nginx config and reload nginx
		getNginxConfig();

		return "Added Location";
	}
}
