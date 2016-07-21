package example

import grails.gorm.multitenancy.Tenants
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver

import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

class BookController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def selectTenant(String tenantId) {
        session.setAttribute(SessionTenantResolver.ATTRIBUTE, tenantId)
        flash.message = "Using Tenant $tenantId"
        redirect(controller:"book")
    }

    def index(Integer max) {
        Tenants.withCurrent {
            params.max = Math.min(max ?: 10, 100)
            respond Book.list(params), model:[bookCount: Book.count()]
        }
    }

    def show(Long id) {
        Tenants.withCurrent {
            Book book = Book.get(id)
            respond book
        }
    }

    def create() {
        Tenants.withCurrent {
            respond new Book(params)
        }
    }

    def save() {
        Tenants.withCurrent {
            Book book = new Book(params)
            Book.withTransaction {
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
        }
    }

    def edit() {
        Tenants.withCurrent {
            Book book = Book.get(id)
            respond book
        }
    }

    def update(Long id) {
        Tenants.withCurrent {
            Book.withTransaction {
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
        }
    }

    def delete(Book book) {
        Tenants.withCurrent {
            Book.withTransaction {

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
