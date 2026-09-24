package cn.limpu.hita.data.model.blog

data class BlogNode(
    val article: BlogArticle,
    val children: List<BlogNode> = emptyList(),
) {
    val isSeries: Boolean get() = children.isNotEmpty()
}

object BlogSeriesGrouper {
    fun group(articles: List<BlogArticle>): List<BlogNode> {
        if (articles.isEmpty()) return emptyList()
        val byPath = LinkedHashMap<String, BlogArticle>()
        for (article in articles) {
            if (article.path.isBlank()) continue
            byPath[article.path] = article
        }
        val childrenByParent = LinkedHashMap<String, MutableList<BlogArticle>>()
        val roots = mutableListOf<BlogArticle>()
        for (article in articles) {
            if (article.path.isBlank()) continue
            val ancestor = nearestAncestorPath(article.path, byPath.keys)
            if (ancestor == null) {
                roots += article
            } else {
                childrenByParent.getOrPut(ancestor) { mutableListOf() }.add(article)
            }
        }
        fun build(article: BlogArticle): BlogNode {
            val kids = childrenByParent[article.path].orEmpty()
                .sortedWith(compareBy<BlogArticle> { it.path }.thenBy { it.pubDateMillis })
                .map { build(it) }
            return BlogNode(article = article, children = kids)
        }
        return roots
            .distinctBy { it.guid }
            .sortedByDescending { it.pubDateMillis }
            .map { build(it) }
    }

    private fun nearestAncestorPath(path: String, known: Set<String>): String? {
        var cursor = parentPath(path)
        while (cursor.isNotEmpty()) {
            if (cursor in known) return cursor
            cursor = parentPath(cursor)
        }
        return null
    }

    private fun parentPath(path: String): String {
        val slash = path.lastIndexOf('/')
        return if (slash <= 0) "" else path.substring(0, slash)
    }
}
