package com.viewo.backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
class StorageConfig(
    @Value("\${do.spaces.endpoint}") private val endpoint: String,
    @Value("\${do.spaces.region}") private val region: String,
    @Value("\${do.spaces.access-key}") private val accessKey: String,
    @Value("\${do.spaces.secret-key}") private val secretKey: String
) {

    @Bean
    fun s3Client(): S3Client {
        // If placeholder credentials are used during dev, it might throw errors if we try to upload.
        // We'll construct it anyway. 
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build()
    }
}
