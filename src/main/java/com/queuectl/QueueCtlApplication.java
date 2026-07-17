package com.queuectl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuectl.cli.RootCommand;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;

@SpringBootApplication
public class QueueCtlApplication implements CommandLineRunner, ExitCodeGenerator {

    private final RootCommand rootCommand;
    private final CommandLine.IFactory factory;
    private int exitCode;

    public QueueCtlApplication(RootCommand rootCommand, CommandLine.IFactory factory) {
        this.rootCommand = rootCommand;
        this.factory = factory;
    }

    public static void main(String[] args) {
        // Every command is a fast one-shot CLI process (WebApplicationType.NONE: no embedded
        // server, no port binding) except `dashboard start`, which needs an embedded Tomcat.
        // The dashboard's --port has to be resolved before the ApplicationContext is built
        // (server.port is read at context-startup time), so it's pre-scanned here rather than
        // left to picocli, which only runs once the context (and Tomcat) already exists.
        boolean isDashboard = args.length > 0 && "dashboard".equals(args[0]);

        SpringApplicationBuilder builder = new SpringApplicationBuilder(QueueCtlApplication.class)
                .web(isDashboard ? WebApplicationType.SERVLET : WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false);

        if (isDashboard) {
            Integer port = findPortArg(args);
            if (port != null) {
                builder.properties("server.port=" + port);
            }
        } else {
            // spring-boot-starter-web is only needed for `dashboard start`. Every other command
            // still has to evaluate its autoconfiguration classes at startup even though
            // WebApplicationType.NONE means none of them activate — that condition-evaluation
            // pass alone was adding multiple seconds to every single CLI invocation. Excluding
            // them outright (rather than relying on the web-application-type condition to fail
            // fast) skips that pass entirely and keeps one-shot commands fast.
            builder.properties(
                    "spring.autoconfigure.exclude="
                            + "org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration");
        }

        System.exit(SpringApplication.exit(builder.run(args)));
    }

    private static Integer findPortArg(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = null;
            if (arg.startsWith("--port=")) {
                value = arg.substring("--port=".length());
            } else if ("--port".equals(arg) && i + 1 < args.length) {
                value = args[i + 1];
            }
            if (value != null) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                    return null; // let picocli surface the real "invalid --port" error
                }
            }
        }
        return null;
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(rootCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
