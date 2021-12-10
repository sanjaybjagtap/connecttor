package com.sap.connector.server.framework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ComponentScan("com.*")
@ImportResource({"classpath:spring_context_beans\\bundle-context.xml",
		"classpath:spring_context_beans\\bundle-context_cust-impl.xml",
		"classpath:spring_context_beans\\bundle-context_bpw.xml",
		"classpath:spring_context_beans\\bundle-context_mock.xml",
		"classpath:spring_context_beans\\bundle-context_bpw-fulfillment.xml",
		"classpath:spring_context_beans\\bundle-context_banksim.xml"})
/**
 * Starting point for the OCB_Connector Spring boot server
 * --> ref plugins/factory and resources/spring_context_beans/*.xml for bean creations
 * --> resources/application.properties for configurations
 * I537791
 */
public class WebSocketDnfApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebSocketDnfApplication.class, args);
	}

}
