package example

import grails.gorm.transactions.TransactionService

class TestService {

    LibraryService libraryService
    TransactionService transactionService

    Boolean testDataService(Serializable id)  {
        libraryService.bookExists(id)
    }

    Person save(String firstName, String lastName) {
        libraryService.addMember(firstName, lastName)
    }
}
