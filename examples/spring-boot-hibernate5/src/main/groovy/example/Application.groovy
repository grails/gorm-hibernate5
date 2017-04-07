package example

import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
@CompileStatic
class Application implements CommandLineRunner {

    static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args)
    }

    @Override
    @Transactional
    void run(String... args) throws Exception {
        new Book(title: "The Stand").save()
        new Book(title: "The Shining").save()
        new Book(title: "It").save()
    }
}
