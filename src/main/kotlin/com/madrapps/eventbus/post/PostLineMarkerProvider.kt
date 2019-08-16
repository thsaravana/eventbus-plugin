package com.madrapps.eventbus.post

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment.RIGHT
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.ui.awt.RelativePoint
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageInfo2UsageAdapter
import com.madrapps.eventbus.getCallExpression
import com.madrapps.eventbus.getParentOfTypeCallExpression
import com.madrapps.eventbus.search
import com.madrapps.eventbus.showPostUsages
import com.madrapps.eventbus.subscribe.isSubscribe
import org.jetbrains.uast.*

class PostLineMarkerProvider : LineMarkerProvider {

    override fun collectSlowLineMarkers(
        elements: MutableList<PsiElement>,
        result: MutableCollection<LineMarkerInfo<PsiElement>>
    ) = Unit

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val uElement = element.toUElement() ?: return null
        if (element !is PsiExpressionStatement) {
            val uCallExpression = uElement.getCallExpression()
            if (uCallExpression != null && uCallExpression.isPost()) {
                val psiIdentifier = uCallExpression.methodIdentifier?.sourcePsi ?: return null
                return PostLineMarkerInfo(psiIdentifier, uCallExpression)
            }
        }
        return null
    }
}

internal fun UsageInfo.isPost(): Boolean {
    val uElement = element.toUElement()
    if (uElement != null) {
        if (uElement.getParentOfType<UImportStatement>() == null) {
            return uElement.getParentOfTypeCallExpression()?.isPost() == true
        }
    }
    return false
}

private fun UCallExpression.isPost() : Boolean {
    return receiverType?.canonicalText == "org.greenrobot.eventbus.EventBus"
            && (methodName == "post" || methodName == "postSticky")
}

private class PostLineMarkerInfo(
    psiElement: PsiElement,
    private val uElement: UCallExpression
) : LineMarkerInfo<PsiElement>(
    psiElement,
    psiElement.textRange,
    IconLoader.getIcon("/icons/greenrobot.png"),
    Pass.LINE_MARKERS,
    null,
    { event, _ ->
        val elementToSearch = (uElement.valueArguments.firstOrNull()
            ?.getExpressionType() as PsiClassReferenceType).resolve()
        if (elementToSearch != null) {
            val collection = search(elementToSearch)
            val usages = collection
                .filter(UsageInfo::isSubscribe)
                .map(::UsageInfo2UsageAdapter)
            if (usages.size == 1) {
                usages.first().navigate(true)
            } else {
                showPostUsages(usages, RelativePoint(event))
            }
        }
    },
    RIGHT
)
