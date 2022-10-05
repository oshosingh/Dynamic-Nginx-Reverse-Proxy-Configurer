package com.nginx.config.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nginx.config.model.NginxConfigModel;

public interface NginxLocationRepo extends JpaRepository<NginxConfigModel, Long>{

	@Query("select max(l.locationId) from NginxConfigModel l")
	Long maxId();

}
