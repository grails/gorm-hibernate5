package example

import groovy.transform.CompileStatic
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
@CompileStatic
class Application {

    static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner init() {
        return { String[] args ->
            Book.withTransaction {
                new Book(title: "The Stand").save()
            }
        } as CommandLineRunner
    }
}
