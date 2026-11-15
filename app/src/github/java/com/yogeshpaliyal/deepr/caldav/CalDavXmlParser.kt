package com.yogeshpaliyal.deepr.caldav

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavResponse(
    val href: String,
    val displayName: String = "",
    val isCalendarCollection: Boolean = false,
    val supportedComponents: Set<String> = emptySet(),
    val currentUserPrincipalHref: String? = null,
    val calendarHomeSetHref: String? = null,
    val etag: String? = null,
    val contentType: String? = null,
    val calendarData: String? = null,
    val resourceTypes: Set<String> = emptySet(),
) {
    fun supportsVTodo(): Boolean {
        if (!isCalendarCollection) return false
        return supportedComponents.isEmpty() ||
            supportedComponents.any { it.equals("VTODO", ignoreCase = true) }
    }
}

object CalDavXmlParser {
    fun parseMultiStatus(xml: String): List<WebDavResponse> {
        if (xml.isBlank()) return emptyList()
        return try {
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(InputSource(StringReader(xml)))
            val root = document.documentElement ?: return emptyList()

            val responseElements = collectElementsByLocalName(root, "response")
            responseElements.mapNotNull { responseElement ->
                parseResponseElement(responseElement)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseResponseElement(element: Element): WebDavResponse? {
        val href = firstChildTextByLocalName(element, "href")?.trim().orEmpty()
        if (href.isEmpty()) return null

        var displayName = ""
        val resourceTypes = mutableSetOf<String>()
        val supportedComponents = mutableSetOf<String>()
        var currentUserPrincipalHref: String? = null
        var calendarHomeSetHref: String? = null
        var etag: String? = null
        var contentType: String? = null
        var calendarData: String? = null

        val propStats = collectElementsByLocalName(element, "propstat")
        for (propStat in propStats) {
            val statusText = firstChildTextByLocalName(propStat, "status").orEmpty()
            if (statusText.isNotEmpty() && !statusText.contains(" 200 ")) {
                continue
            }
            val prop = collectElementsByLocalName(propStat, "prop").firstOrNull() ?: continue

            firstChildTextByLocalName(prop, "displayname")?.trim()?.let {
                if (it.isNotEmpty()) displayName = it
            }

            firstChildTextByLocalName(prop, "getetag")?.trim()?.let {
                if (it.isNotEmpty()) etag = it
            }

            firstChildTextByLocalName(prop, "getcontenttype")?.trim()?.let {
                if (it.isNotEmpty()) contentType = it
            }

            firstChildTextByLocalName(prop, "calendar-data")?.let {
                if (it.isNotBlank()) calendarData = it
            }

            firstChildElementByLocalName(prop, "resourcetype")?.let { resTypeEl ->
                val children = resTypeEl.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        val name = localNameOf(child)
                        if (name.isNotEmpty()) {
                            resourceTypes.add(name.lowercase())
                        }
                    }
                }
            }

            firstChildElementByLocalName(prop, "supported-calendar-component-set")?.let { compSetEl ->
                val compElements = collectElementsByLocalName(compSetEl, "comp")
                for (comp in compElements) {
                    val compName = comp.getAttribute("name").trim()
                    if (compName.isNotEmpty()) {
                        supportedComponents.add(compName.uppercase())
                    }
                }
            }

            firstChildElementByLocalName(prop, "current-user-principal")?.let { principalEl ->
                val pHref = firstChildTextByLocalName(principalEl, "href")?.trim()
                if (!pHref.isNullOrEmpty()) {
                    currentUserPrincipalHref = pHref
                }
            }

            firstChildElementByLocalName(prop, "calendar-home-set")?.let { homeEl ->
                val hHref = firstChildTextByLocalName(homeEl, "href")?.trim()
                if (!hHref.isNullOrEmpty()) {
                    calendarHomeSetHref = hHref
                }
            }
        }

        return WebDavResponse(
            href = href,
            displayName = displayName,
            isCalendarCollection = resourceTypes.contains("calendar"),
            supportedComponents = supportedComponents,
            currentUserPrincipalHref = currentUserPrincipalHref,
            calendarHomeSetHref = calendarHomeSetHref,
            etag = etag,
            contentType = contentType,
            calendarData = calendarData,
            resourceTypes = resourceTypes,
        )
    }

    private fun localNameOf(node: Node): String {
        val local = node.localName
        if (!local.isNullOrBlank()) return local
        val fullName = node.nodeName ?: return ""
        val colonIdx = fullName.indexOf(':')
        return if (colonIdx >= 0) fullName.substring(colonIdx + 1) else fullName
    }

    private fun collectElementsByLocalName(
        parent: Element,
        targetLocalName: String,
    ): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (localNameOf(el).equals(targetLocalName, ignoreCase = true)) {
                    result.add(el)
                }
            }
        }
        return result
    }

    private fun firstChildElementByLocalName(
        parent: Element,
        targetLocalName: String,
    ): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (localNameOf(el).equals(targetLocalName, ignoreCase = true)) {
                    return el
                }
            }
        }
        return null
    }

    private fun firstChildTextByLocalName(
        parent: Element,
        targetLocalName: String,
    ): String? = firstChildElementByLocalName(parent, targetLocalName)?.textContent
}
