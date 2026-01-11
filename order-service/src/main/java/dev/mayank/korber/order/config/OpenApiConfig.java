package dev.mayank.korber.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SuppressWarnings("unused")
public class OpenApiConfig {
    @Bean
    public OpenAPI orderServiceAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:8080");
        devServer.setDescription("Development Server");

        Contact contact = new Contact();
        contact.setEmail("ce.mayank8@gamil.com");
        contact.setName("Mayank");
        contact.setUrl("https://github.com/MayankGupta-dev08");

        License mitLicense = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("Order Service API")
                .version("1.0.0")
                .contact(contact)
                .description("This API manages customer orders in an e-commerce system. " +
                        "It communicates with the Inventory Service to check availability and reserve stock. " +
                        "Orders are processed using FIFO (First In First Out) inventory allocation strategy.")
                .termsOfService("https://www.korber.com/terms")
                .license(mitLicense);

        return new OpenAPI()
                .info(info)
                .servers(List.of(devServer));
    }
}
