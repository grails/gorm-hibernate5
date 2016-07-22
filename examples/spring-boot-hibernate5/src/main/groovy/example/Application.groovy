package example

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

/**
 * Created by graemerocher on 22/07/2016.
 */
@SpringBootApplication
class Application {

    public static void main(String[] args) throws Exception {
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
