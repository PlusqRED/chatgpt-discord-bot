# Start from a base image
FROM azul/zulu-openjdk:21 as build

# Set the working directory
WORKDIR /app

# Copy the Gradle wrapper
COPY gradlew .
COPY gradle gradle

# Give execute permission to the Gradle wrapper
RUN chmod +x ./gradlew

# Copy only the files that define your dependencies
COPY build.gradle settings.gradle ./

# Download dependencies
RUN ./gradlew dependencies

# Copy the rest of your project files
COPY . .

# Use Gradle to build the project
RUN ./gradlew build

# Start a new stage to minimize the final image size
FROM azul/zulu-openjdk:21

# Create a group and user
RUN groupadd app && useradd -g app app

# Change the ownership of the /app directory to our new user
RUN mkdir /app && chown -R app:app /app

# Switch to the new user
USER app

WORKDIR /app

# Copy the built JAR file from the previous stage
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar

# Specify the command to run the application
CMD ["java", "-jar", "app.jar"]