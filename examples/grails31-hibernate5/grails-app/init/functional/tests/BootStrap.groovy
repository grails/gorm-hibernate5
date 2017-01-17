package functional.tests

import functional.tests.Product

class BootStrap {

    def init = { servletContext ->
        new Product(name: "MacBook", price: "1200.01").save(flush:true)
    }
    def destroy = {
    }
}
