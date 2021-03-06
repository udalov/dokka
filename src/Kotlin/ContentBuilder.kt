package org.jetbrains.dokka

import java.util.ArrayDeque
import org.jetbrains.jet.lang.descriptors.*
import org.jetbrains.jet.lang.resolve.*
import org.jetbrains.jet.lang.resolve.scopes.*
import org.jetbrains.jet.lang.resolve.name.*
import org.intellij.markdown.*

public fun DocumentationBuilder.buildContent(tree: MarkdownNode, descriptor: DeclarationDescriptor): Content {
//    println(tree.toTestString())
    val nodeStack = ArrayDeque<ContentNode>()
    nodeStack.push(Content())

    tree.visit {(node, processChildren) ->
        val parent = nodeStack.peek()!!
        when (node.type) {
            MarkdownElementTypes.UNORDERED_LIST -> {
                nodeStack.push(ContentList())
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownElementTypes.ORDERED_LIST -> {
                nodeStack.push(ContentList()) // TODO: add list kind
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownElementTypes.LIST_ITEM -> {
                nodeStack.push(ContentListItem())
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownElementTypes.EMPH -> {
                nodeStack.push(ContentEmphasis())
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownElementTypes.STRONG -> {
                nodeStack.push(ContentStrong())
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownTokenTypes.CODE -> {
                nodeStack.push(ContentCode())
                processChildren()
                parent.append(nodeStack.pop())
            }
/*
            MarkdownElementTypes.DIRECTIVE -> {
                val name = tree.findChildByType(node, MarkdownElementTypes.DIRECTIVE_NAME)?.let { tree.getNodeText(it) } ?: ""
                val params = tree.findChildByType(node, MarkdownElementTypes.DIRECTIVE_PARAMS)?.let { tree.getNodeText(it) } ?: ""
                when (name) {
                    "code" -> parent.append(functionBody(descriptor, params))
                }
            }
*/
            MarkdownElementTypes.SECTION -> {
                val label = node.child(MarkdownTokenTypes.SECTION_ID)?.let { it.text.trimLeading("$").trim("{","}") } ?: ""
                nodeStack.push(ContentSection(label))
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownElementTypes.INLINE_LINK -> {
                val label = node.child(MarkdownElementTypes.LINK_TEXT)?.child(MarkdownTokenTypes.TEXT)
                val destination = node.child(MarkdownElementTypes.LINK_DESTINATION)
                if (label != null) {
                    if (destination != null) {
                        val link = ContentExternalLink(destination.text)
                        link.append(ContentText(label.text))
                        parent.append(link)
                    } else {
                        val link = ContentExternalLink(label.text)
                        link.append(ContentText(label.text))
                        parent.append(link)
                    }
                }
            }
            MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                val label = node.child(MarkdownElementTypes.LINK_LABEL)?.child(MarkdownTokenTypes.TEXT)
                if (label != null) {
                    val link = ContentExternalLink(label.text)
                    link.append(ContentText(label.text))
                    parent.append(link)
                }
            }
            MarkdownTokenTypes.WHITE_SPACE,
            MarkdownTokenTypes.EOL -> {
                if (nodeStack.peek() is ContentParagraph && node.parent?.children?.last() != node) {
                    nodeStack.push(ContentText(node.text))
                    processChildren()
                    parent.append(nodeStack.pop())
                }
            }
            MarkdownTokenTypes.TEXT -> {
                nodeStack.push(ContentText(node.text))
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownElementTypes.PARAGRAPH -> {
                nodeStack.push(ContentParagraph())
                processChildren()
                parent.append(nodeStack.pop())
            }
            MarkdownTokenTypes.COLON -> {
                // TODO fix markdown parser
                if (!isColonAfterSectionLabel(node)) {
                    parent.append(ContentText(node.text))
                }
            }
            MarkdownTokenTypes.DOUBLE_QUOTE,
            MarkdownTokenTypes.LT,
            MarkdownTokenTypes.GT -> {
                parent.append(ContentText(node.text))
            }
            else -> {
                processChildren()
            }
        }
    }
    return nodeStack.pop() as Content
}


fun DocumentationBuilder.functionBody(descriptor: DeclarationDescriptor, functionName: String): ContentNode {
    val scope = getResolutionScope(descriptor)
    val rootPackage = session.getModuleDescriptor().getPackage(FqName.ROOT)!!
    val rootScope = rootPackage.getMemberScope()
    val symbol = resolveInScope(functionName, scope) ?: resolveInScope(functionName, rootScope)
    if (symbol == null)
        return ContentBlockCode().let() { it.append(ContentText("Unresolved: $functionName")); it }
    val psiElement = DescriptorToSourceUtils.descriptorToDeclaration(symbol)
    if (psiElement == null)
        return ContentBlockCode().let() { it.append(ContentText("Source not found: $functionName")); it }

    return ContentBlockCode().let() { it.append(ContentText(psiElement.getText())); it }
}

private fun DocumentationBuilder.resolveInScope(functionName: String, scope: JetScope): DeclarationDescriptor? {
    var currentScope = scope
    val parts = functionName.split('.')
    var symbol: DeclarationDescriptor? = null

    for (part in parts) {
        // short name
        val symbolName = Name.guess(part)
        val partSymbol = currentScope.getLocalVariable(symbolName) ?:
                currentScope.getProperties(symbolName).firstOrNull() ?:
                currentScope.getFunctions(symbolName).firstOrNull() ?:
                currentScope.getClassifier(symbolName) ?:
                currentScope.getPackage(symbolName)

        if (partSymbol == null) {
            symbol = null
            break
        }
        currentScope = getResolutionScope(partSymbol)
        symbol = partSymbol
    }

    return symbol
}

private fun isColonAfterSectionLabel(node: MarkdownNode): Boolean {
    val parent = node.parent
    return parent != null && parent.type == MarkdownElementTypes.SECTION && parent.children.size() >= 2 &&
            node == parent.children[1];
}
