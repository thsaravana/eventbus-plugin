package com.madrapps.eventbus.subscribe

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.RIGHT
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.usageView.UsageInfo
import com.intellij.usages.*
import com.intellij.util.toArray
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility.PUBLIC
import org.jetbrains.uast.toUElement
import java.awt.event.MouseEvent


class SubscribeLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val uElement = element.toUElement()
        if (uElement is UMethod) {
            val annotation = uElement.annotations.find { it.qualifiedName == "org.greenrobot.eventbus.Subscribe" }
            if (annotation != null) {
                if (uElement.visibility == PUBLIC && uElement.uastParameters.size == 1) {
                    val psiElement = uElement.uastAnchor?.sourcePsi
                    if (psiElement != null) {
                        return SubscribeLineMarkerInfo(psiElement, "post for ---", uElement)
                    }
                }
            }
        }
        return null
    }
}

private class SubscribeLineMarkerInfo(
    psiElement: PsiElement,
    private val message: String,
    private val uElement: UMethod
) : LineMarkerInfo<PsiElement>(
    psiElement,
    psiElement.textRange,
    AllIcons.General.ArrowLeft,
    { message },
    null,
    RIGHT
) {

    override fun createGutterRenderer(): GutterIconRenderer? {
        return object : LineMarkerGutterIconRenderer<PsiElement>(this) {
            override fun getClickAction(): AnAction? {
                return object : AnAction(message) {
                    override fun actionPerformed(e: AnActionEvent) {
                        val relativePoint = RelativePoint(e.inputEvent as MouseEvent)

                        val elementToSearch = (uElement.uastParameters[0].type as PsiClassReferenceType).reference.resolve()
                        if (elementToSearch != null) {
                            val collection = search(elementToSearch)

                            val usages = collection.map {
                                val usage: Usage = UsageInfo2UsageAdapter(it)
                                usage
                            }

                            showUsages(usages, relativePoint)
                        }
                    }
                }
            }
        }
    }

    private fun showUsages(
        usages: List<Usage>,
        relativePoint: RelativePoint
    ) {
        JBPopupFactory.getInstance().createListPopup(BaseListPopupStep("Usages", usages))
            .show(relativePoint)

        val toArray = usages.toArray(arrayOfNulls<Usage>(usages.size))
        val usageViewPresentation = UsageViewPresentation()
        usageViewPresentation.tabText = "Type"
        usageViewPresentation.isOpenInNewTab = false
        usageViewPresentation.isCodeUsages = false
        usageViewPresentation.isUsageTypeFilteringAvailable = false
        usageViewPresentation.codeUsagesString = "codeUsagesString"
        usageViewPresentation.contextText = "contextText"
        usageViewPresentation.nonCodeUsagesString = "nonCodeUsagesString"
        usageViewPresentation.scopeText = "scopeTest"
        usageViewPresentation.targetsNodeText = "targetsNodeText"
        val instance = UsageViewManager.getInstance(element?.project!!)
        instance.showUsages(
            UsageTarget.EMPTY_ARRAY,
            toArray,
            usageViewPresentation
        )
    }
}

fun search(element: PsiElement): Collection<UsageInfo> {
    val references = ReferencesSearch.search(element).findAll()
    return references.map(::UsageInfo)
}