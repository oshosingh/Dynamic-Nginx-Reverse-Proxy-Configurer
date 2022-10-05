package com.nginx.config.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.nginx.config.model.NginxConfigModel;
import com.nginx.config.repo.NginxLocationRepo;
import com.nginx.config.request.NginxProxyInput;

@Service
public class NginxConfigService {
	
	@Autowired
	private NginxLocationRepo nginxLocationRepo;
	
	@Value("${nginx.conf.path}")
	private String nginxWriteConfPath;
	
	private String config;

	public String getNginxConfig() {
//		List<Integer> lis = Arrays.asList(1,2,3,4,5,6,7,8,9,10,11,12);
		List<NginxConfigModel> lis = nginxLocationRepo.findAll();
		System.out.println("List values are "+ lis);
		
//		List<List<Integer>> partition = new ArrayList<>();
		List<List<NginxConfigModel>> partition = new ArrayList<>();
		int partitionSize = 4;
		
		for(int i=0; i<lis.size(); i+=partitionSize) {
			partition.add(lis.subList(i, i + Math.min(partitionSize, lis.size()-i)));
		}
		
		config = "server { \n"
				+ "	 listen 80; \n"
				+ "\t listen [::]:8080; \n"
				+ "	 server_name _; \n\n";
		
		genReverseProxyConfig(partition, 3);
		
		config += "}";
		
		writeToFile();
		
		System.out.println("Config generated");
		
		return config;
	}
	
	private void writeToFile() {
		Path path = Paths.get(nginxWriteConfPath);
		try {
			Files.writeString(path, config ,StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private void genReverseProxyConfig(List<List<NginxConfigModel>> partition, int threadCount) {
		ExecutorService executors = Executors.newFixedThreadPool(threadCount);
		
		for(List<NginxConfigModel> set : partition) {
			executors.execute(() -> {
				for(NginxConfigModel val : set) {
					//System.out.println("Thread name : "+ Thread.currentThread().getName() + " id val : " + val);
					addLocationBlocks(getLocationBlock(val));
				}
			});
		}
		
		// Shutdown the Executor, so it cannot receive more threads
		executors.shutdown();
		
		// Causes the main thread to wait until all executor threads are done executing their tasks
		try {
			executors.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}

	private synchronized void addLocationBlocks(String locationBlock) {
		//System.out.println("Thread working : "+ Thread.currentThread().getName());
		config += locationBlock;
		//System.out.println("Thread Done : "+ Thread.currentThread().getName());
		//System.out.println(config);
	}

	private String getLocationBlock(NginxConfigModel nginxConfigModel) {
		return "\t location /"+nginxConfigModel.getLocationPath()+" { \n"
				+ "	\t proxy_pass: "+nginxConfigModel.getProxyUrl()+"; \n"
				+ "\t } \n \n";
	}

	public String addLocation(NginxProxyInput nginxProxyInput) {
		Long locationId = nginxLocationRepo.maxId();
		locationId = locationId == null ? 1 : locationId+1;
		
		NginxConfigModel model = new NginxConfigModel();
		model.setLocationId(locationId);
		model.setLocationPath(nginxProxyInput.getProxyPath());
		model.setProxyUrl(nginxProxyInput.getProxyUrl());
		
		nginxLocationRepo.saveAndFlush(model);
		
		return "Added Location";
	}
}
