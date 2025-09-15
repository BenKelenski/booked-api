# Stage 1: Build the custom Java runtime
FROM eclipse-temurin:21-jdk-alpine AS jre-build

# Create a custom Java runtime
RUN apk add --no-cache binutils
RUN $JAVA_HOME/bin/jlink \
         --add-modules ALL-MODULE-PATH \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

# Stage 2: Build the final image
FROM alpine:3.14
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Copy the custom JRE
COPY --from=jre-build /javaruntime $JAVA_HOME

# Create a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set up application directory
WORKDIR /opt/app
COPY build/libs/booked-api-1.0-all.jar app.jar

# Use non-root user
USER appuser

# Set health check
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/api/v1/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]