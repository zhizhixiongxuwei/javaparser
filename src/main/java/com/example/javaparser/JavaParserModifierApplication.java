package com.example.javaparser;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JavaParserModifierApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(JavaParserModifierApplication.class);

    private final JavaModifierProcessor processor;

    public JavaParserModifierApplication(JavaModifierProcessor processor) {
        this.processor = processor;
    }

    public static void main(String[] args) {
        SpringApplication.run(JavaParserModifierApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length < 2) {
            log.error("Usage: <inputDir> <outputDir>");
            log.error("Example: java -jar app.jar D:/input/src D:/output/src");
            return;
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = Paths.get(args[1]).toAbsolutePath().normalize();

        processor.process(inputRoot, outputRoot);
    }
}
