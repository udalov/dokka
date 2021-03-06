package org.jetbrains.dokka

import org.jetbrains.dokka.symbol
import org.jetbrains.dokka.text
import org.jetbrains.dokka.identifier
import org.jetbrains.dokka.link
import org.jetbrains.dokka.keyword
import org.jetbrains.dokka.LanguageService
import org.jetbrains.dokka.DocumentationNode
import org.jetbrains.dokka.ContentNode
import org.jetbrains.dokka
import org.jetbrains.dokka.ContentText

/**
 * Implements [LanguageService] and provides rendering of symbols in Kotlin language
 */
class KotlinLanguageService : LanguageService {
    override fun render(node: DocumentationNode): ContentNode {
        return dokka.content {
            when (node.kind) {
                DocumentationNode.Kind.Package -> renderPackage(node)
                DocumentationNode.Kind.Class,
                DocumentationNode.Kind.Interface,
                DocumentationNode.Kind.Enum,
                DocumentationNode.Kind.EnumItem,
                DocumentationNode.Kind.Object -> renderClass(node)

                DocumentationNode.Kind.TypeParameter -> renderTypeParameter(node)
                DocumentationNode.Kind.Type,
                DocumentationNode.Kind.UpperBound -> renderType(node)

                DocumentationNode.Kind.Modifier -> renderModifier(node)
                DocumentationNode.Kind.Constructor,
                DocumentationNode.Kind.Function,
                DocumentationNode.Kind.ClassObjectFunction -> renderFunction(node)
                DocumentationNode.Kind.Property,
                DocumentationNode.Kind.ClassObjectProperty -> renderProperty(node)
                else -> ContentText("${node.kind}: ${node.name}")
            }
        }
    }

    override fun renderName(node: DocumentationNode): String {
        return when (node.kind) {
            DocumentationNode.Kind.Constructor -> node.owner!!.name
            else -> node.name
        }
    }

    private fun ContentNode.renderPackage(node: DocumentationNode) {
        keyword("package")
        text(" ")
        identifier(node.name)
    }

    private fun ContentNode.renderList(nodes: List<DocumentationNode>, separator: String = ", ", renderItem: (DocumentationNode) -> Unit) {
        if (nodes.none())
            return
        renderItem(nodes.first())
        nodes.drop(1).forEach {
            symbol(separator)
            renderItem(it)
        }
    }

    private fun ContentNode.renderLinked(node: DocumentationNode, body: ContentNode.(DocumentationNode)->Unit) {
        val to = node.links.firstOrNull()
        if (to == null)
            body(node)
        else
            link(to) {
                body(node)
            }
    }

    private fun ContentNode.renderType(node: DocumentationNode) {
        val typeArguments = node.details(DocumentationNode.Kind.Type)
        if (node.name == "Function${typeArguments.count() - 1}") {
            // lambda
            symbol("(")
            renderList(typeArguments.take(typeArguments.size - 1)) {
                renderType(it)
            }
            symbol(")")
            text(" ")
            symbol("->")
            text(" ")
            renderType(typeArguments.last())
            return
        }
        if (node.name == "ExtensionFunction${typeArguments.count() - 2}") {
            // extension lambda
            renderType(typeArguments.first())
            symbol(".")
            symbol("(")
            renderList(typeArguments.drop(1).take(typeArguments.size - 2)) {
                renderType(it)
            }
            symbol(")")
            text(" ")
            symbol("->")
            text(" ")
            renderType(typeArguments.last())
            return
        }
        renderLinked(node) { identifier(it.name) }
        if (typeArguments.any()) {
            symbol("<")
            renderList(typeArguments) {
                renderType(it)
            }
            symbol(">")
        }
    }

    private fun ContentNode.renderModifier(node: DocumentationNode) {
        when (node.name) {
            "final", "internal" -> {}
            else -> {
                keyword(node.name)
                text(" ")
            }
        }
    }

    private fun ContentNode.renderTypeParameter(node: DocumentationNode) {
        val constraints = node.details(DocumentationNode.Kind.UpperBound)
        identifier(node.name)
        if (constraints.any()) {
            symbol(" : ")
            renderList(constraints) {
                renderType(it)
            }
        }
    }

    private fun ContentNode.renderParameter(node: DocumentationNode) {
        identifier(node.name)
        symbol(": ")
        val parameterType = node.detail(DocumentationNode.Kind.Type)
        renderType(parameterType)
    }

    private fun ContentNode.renderTypeParametersForNode(node: DocumentationNode) {
        val typeParameters = node.details(DocumentationNode.Kind.TypeParameter)
        if (typeParameters.any()) {
            symbol("<")
            renderList(typeParameters) {
                renderType(it)
            }
            symbol("> ")
        }
    }

    private fun ContentNode.renderSupertypesForNode(node: DocumentationNode) {
        val supertypes = node.details(DocumentationNode.Kind.Supertype)
        if (supertypes.any()) {
            symbol(" : ")
            renderList(supertypes) {
                renderType(it)
            }
        }
    }

    private fun ContentNode.renderModifiersForNode(node: DocumentationNode) {
        val modifiers = node.details(DocumentationNode.Kind.Modifier)
        for (it in modifiers) {
            if (node.kind == org.jetbrains.dokka.DocumentationNode.Kind.Interface && it.name == "abstract")
                continue
            renderModifier(it)
        }
    }

    private fun ContentNode.renderClass(node: DocumentationNode) {
        renderModifiersForNode(node)
        when (node.kind) {
            DocumentationNode.Kind.Class -> keyword("class ")
            DocumentationNode.Kind.Interface -> keyword("trait ")
            DocumentationNode.Kind.Enum -> keyword("enum class ")
            DocumentationNode.Kind.EnumItem -> keyword("enum val ")
            DocumentationNode.Kind.Object -> keyword("object ")
            else -> throw IllegalArgumentException("Node $node is not a class-like object")
        }

        identifier(node.name)
        renderTypeParametersForNode(node)
        renderSupertypesForNode(node)
    }

    private fun ContentNode.renderFunction(node: DocumentationNode) {
        renderModifiersForNode(node)
        when (node.kind) {
            DocumentationNode.Kind.Constructor -> identifier(node.owner!!.name)
            DocumentationNode.Kind.Function,
            DocumentationNode.Kind.ClassObjectFunction -> keyword("fun ")
            else -> throw IllegalArgumentException("Node $node is not a function-like object")
        }
        renderTypeParametersForNode(node)
        val receiver = node.details(DocumentationNode.Kind.Receiver).singleOrNull()
        if (receiver != null) {
            renderType(receiver.detail(DocumentationNode.Kind.Type))
            symbol(".")
        }

        if (node.kind != org.jetbrains.dokka.DocumentationNode.Kind.Constructor)
            identifier(node.name)

        symbol("(")
        renderList(node.details(DocumentationNode.Kind.Parameter)) {
            renderParameter(it)
        }
        symbol(")")
        if (node.kind != org.jetbrains.dokka.DocumentationNode.Kind.Constructor) {
            symbol(": ")
            renderType(node.detail(DocumentationNode.Kind.Type))
        }
    }

    private fun ContentNode.renderProperty(node: DocumentationNode) {
        renderModifiersForNode(node)
        when (node.kind) {
            DocumentationNode.Kind.Property,
            DocumentationNode.Kind.ClassObjectProperty -> keyword("val ")
            else -> throw IllegalArgumentException("Node $node is not a property")
        }
        renderTypeParametersForNode(node)
        val receiver = node.details(DocumentationNode.Kind.Receiver).singleOrNull()
        if (receiver != null) {
            renderType(receiver.detail(DocumentationNode.Kind.Type))
            symbol(".")
        }

        identifier(node.name)
        symbol(": ")
        renderType(node.detail(DocumentationNode.Kind.Type))
    }
}