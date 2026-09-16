FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace/app

COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY src src

# Make gradlew executable
RUN chmod +x ./gradlew

# Build the application skipping tests
RUN ./gradlew build -x test

# Create the final image
FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
COPY --from=build /workspace/app/build/libs/*.jar app.jar

# Run the jar file 
ENTRYPOINT ["java","-jar","/app.jar"]
