package com.rentrix.rentrixserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Config {
	
	private final StorageProperties props;
	
	@Bean
	public AwsCredentialsProvider awsCredentialsProvider() {
		if (props.getAccessKey() != null && !props.getAccessKey().isBlank()
				 && props.getSecretKey() != null && !props.getSecretKey().isBlank()) {
			return StaticCredentialsProvider
						 .create(AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}
	
	@Bean
	public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
		return S3Client.builder()
							.region(Region.of(props.getRegion()))
							.credentialsProvider(credentialsProvider)
							.build();
	}
	
	@Bean
	public S3Presigner s3Presigner(AwsCredentialsProvider credentialsProvider) {
		return S3Presigner.builder()
								.region(Region.of(props.getRegion()))
								.credentialsProvider(credentialsProvider)
								.build();
	}
	
}