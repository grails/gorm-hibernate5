package example

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver

import static org.springframework.http.HttpStatus.*

@CurrentTenant
class BookController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    @WithoutTenant
    def selectTenant(String tenantId) {
        session.setAttribute(SessionTenantResolver.ATTRIBUTE, tenantId)
        flash.message = "Using Tenant $tenantId"
        redirect(controller:"book")
    }

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond Book.list(params), model:[bookCount: Book.count()]
    }

    def show(Long id) {
        Book book = Book.get(id)
        respond book
    }

    def create() {
        respond new Book(params)
    }

    @Transactional
    def save() {
        Book book = new Book(params)
        if (book == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        if (book.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond book.errors, view:'create'
            return
        }

        book.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'book.label', default: 'Book'), book.id])
                redirect book
            }
            '*' { respond book, [status: CREATED] }
        }
    }

    def edit() {
        Book book = Book.get(id)
        respond book
    }

    @Transactional
    def update(Long id) {
        Book book = Book.get(id)
        if (book == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        if (book.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond book.errors, view:'edit'
            return
        }

        book.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'book.label', default: 'Book'), book.id])
                redirect book
            }
            '*'{ respond book, [status: OK] }
        }
    }

    @Transactional
    def delete(Book book) {
        if (book == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        book.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'book.label', default: 'Book'), book.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'book.label', default: 'Book'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
