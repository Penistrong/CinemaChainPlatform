package com.penistrong.CinemaChainPlatform;

import com.penistrong.CinemaChainPlatform.online.datamanager.DataManager;
import com.penistrong.CinemaChainPlatform.online.util.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;

@SpringBootApplication
public class CinemaChainPlatformApplication{

	public static void main(String[] args) throws Exception{
		//Initialize
		String classPath = null;
		try {
			classPath = ResourceUtils.getURL("classpath:").getPath();
		}catch (FileNotFoundException e){
			e.printStackTrace();
		}
		//As normal, the classPath is "**/target/classes/"
		System.out.println("Resource classPath:" + classPath);
		
		//Load all the data into DataManager
		DataManager.getInstance().loadData(classPath + "resources/dataset/movies.csv",
				classPath + "resources/dataset/links.csv",
				classPath + "resources/dataset/ratings.csv",
				classPath + "resources/modeldata/item2vecEmb.csv",
				classPath + "resources/modeldata/userEmb.csv",
				Config.MOVIE_GRAPH_EMBEDDING_PREFIX_IN_REDIS,
				Config.USER_EMBEDDING_PREFIX_IN_REDIS);

		//Server Start
		SpringApplication.run(CinemaChainPlatformApplication.class, args);
	}

}
