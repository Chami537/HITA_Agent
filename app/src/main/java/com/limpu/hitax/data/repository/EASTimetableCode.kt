package com.limpu.hitax.data.repository

import com.limpu.hitax.data.model.eas.EASToken
import com.limpu.hitax.data.model.eas.TermItem

object EASTimetableCode {
    fun of(campus: EASToken.Campus, term: TermItem): String {
        return "${campus.name}:${term.getCode()}"
    }

    fun candidates(term: TermItem, campus: EASToken.Campus? = null): List<String> {
        val preferred = campus?.let { of(it, term) }
        return listOfNotNull(preferred)
            .plus(term.getCode())
            .distinct()
    }
}
