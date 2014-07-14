package org.jetbrains.dokka

public class JekyllFormatService(locationService: LocationService, signatureGenerator: SignatureGenerator)
: MarkdownFormatService(locationService, signatureGenerator) {
    override val extension: String = "html"
    override fun format(nodes: Iterable<DocumentationNode>, to: StringBuilder) {
        to.appendln("---")
        to.appendln("layout: post")
        to.appendln("title: ${nodes.first().name}")
        to.appendln("---")
        super<MarkdownFormatService>.format(nodes, to)
    }
}