import example.LoginAuthenticationSucessHandler

// Place your Spring DSL code here
beans = {

    restAuthenticationSuccessHandler(LoginAuthenticationSucessHandler) {
        testService = ref('testService')
    }
}
