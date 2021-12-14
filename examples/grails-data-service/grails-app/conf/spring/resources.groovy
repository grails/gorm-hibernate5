import example.LoginAuthenticationSucessHandler
import example.TestBean

// Place your Spring DSL code here
beans = {

    restAuthenticationSuccessHandler(LoginAuthenticationSucessHandler) {
        testService = ref('testService')
    }

    testBean(TestBean)
}
