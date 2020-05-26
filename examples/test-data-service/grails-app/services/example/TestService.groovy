package example

class TestService {

    LibraryService libraryService

    Boolean testDataService(Serializable id)  {
        libraryService.bookExists(id)
    }
}
