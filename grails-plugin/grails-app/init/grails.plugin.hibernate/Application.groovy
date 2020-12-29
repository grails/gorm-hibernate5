package grails.plugin.hibernate

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import grails.plugins.metadata.PluginSource
import groovy.transform.CompileStatic

@PluginSource
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run((Class)Application, args)
    }
}