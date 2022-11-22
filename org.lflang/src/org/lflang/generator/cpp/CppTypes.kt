/*
 * Copyright (c) 2022, TU Dresden.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.lflang.generator.cpp

import org.lflang.InferredType
import org.lflang.TimeValue
import org.lflang.generator.TargetTypes
import org.lflang.lf.Initializer
import org.lflang.lf.ParameterReference

object CppTypes : TargetTypes {

    override fun supportsGenerics() = true

    override fun getTargetTimeType() = "reactor::Duration"
    override fun getTargetTagType() = "reactor::Tag"

    override fun getTargetFixedSizeListType(baseType: String, size: Int) = "std::array<$baseType, $size>"
    override fun getTargetVariableSizeListType(baseType: String) = "std::vector<$baseType>"

    override fun getTargetUndefinedType() = "void"

    override fun getTargetTimeExpr(timeValue: TimeValue): String =
        with(timeValue) {
            if (magnitude == 0L) "reactor::Duration::zero()"
            else magnitude.toString() + unit.cppUnit
        }

}

/**
 * Returns the initializer list used in direct initialization in ctor definition.
 */
fun CppTypes.getCppInitializerList(init: Initializer?, inferredType: InferredType?): String {
    if (init == null) {
        return getMissingExpr(inferredType)
    }
    val singleExpr = init.exprs.singleOrNull()
    return buildString {
        if (init.isBraces.not() && singleExpr != null) {
            append("(").append(getTargetExpr(singleExpr, inferredType)).append(")")
        } else {
            val (prefix, postfix) = if (init.isBraces) Pair("{", "}") else Pair("(", ")")
            init.exprs.joinTo(this, ", ", prefix, postfix) {
                getTargetExpr(it, inferredType?.componentType)
            }
        }
    }
}

fun CppTypes.getCppStandaloneInitializer(init: Initializer?, inferredType: InferredType?): String {
    if (init == null) {
        return getMissingExpr(inferredType)
    }

    return buildString {
        append(getTargetType(inferredType)) // treat as ctor call
        val (prefix, postfix) = if (init.isBraces) Pair("{", "}") else Pair("(", ")")
        init.exprs.joinTo(this, ", ", prefix, postfix) {
            getTargetExpr(it, inferredType?.componentType)
        }
    }
}

/**
 * This object generates types in the context of the outer class,
 * where parameter references need special handling.
 */
object CppOuterTypes : TargetTypes by CppTypes {

    override fun getTargetParamRef(expr: ParameterReference, type: InferredType?): String {
        return "__lf_inner.${expr.parameter.name}"
    }

}
