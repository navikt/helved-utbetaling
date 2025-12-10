package libs.kafka

import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.TopologyDescription
import org.apache.kafka.streams.TopologyDescription.*

class TopologyVisulizer(private val topology: Topology) {
    fun uml() : String = PlantUML.generate(topology)
    fun mermaid(): Mermaid = Mermaid(topology)
    fun desc(): String = topology.describe().toString()
}

internal object PlantUML {
    internal fun generate(topology: Topology): String = topology.describe().let { description ->
        val stores = description.globalStores().map { processStore(it) }.toSet()
        val subtopologies = description.subtopologies().map { processSubtopology(it) }
        val queues = description.getTopics().map { formatTopicToUmlQueue(it) }.toSet()
        val uml = UML(stores + subtopologies, queues)
        uml.toString()
    }

    private fun TopologyDescription.getTopics(): Set<String> = this.let {
        val storeTopics = this.globalStores().flatMap { store -> store.source().topicSet() }
        val subtopologyTopics = this.subtopologies().flatMap { it.getTopics() }
        storeTopics.plus(subtopologyTopics).toSet()
    }

    private fun TopologyDescription.Subtopology.getTopics(): Set<String> = this.nodes().let { nodes ->
        val sinkTopics = nodes.filterIsInstance<TopologyDescription.Sink>().map { it.topic() }
        val sourceTopics = nodes.filterIsInstance<TopologyDescription.Source>().flatMap { it.topicSet() }
        sinkTopics.plus(sourceTopics).toSet()
    }

    private fun processStore(store: TopologyDescription.GlobalStore): Package {
        val source = processNode(store.source())
        val processor = processNode(store.processor())
        val (declarations, relations) = source.join(processor)
        return Package(store.id(), declarations, relations)
    }

    private fun processSubtopology(subtopology: TopologyDescription.Subtopology): Package {
        val agents = subtopology.nodes()
            .map { node -> formatNodeToUmlAgent(node.name(), node::class.simpleName!!) }
            .toSet()
        val downstreamRelations = subtopology.nodes()
            .flatMap { node -> node.successors().map { node.name() to it.name() } }
            .map { (node, downstreamNode) -> formatNodeToUmlRelation(node, downstreamNode) }
            .toSet()
        val nodes = subtopology.nodes()
            .map { processNode(it) }
            .reduce { acc, pair -> Pair(acc.first.union(pair.first), acc.second.union(pair.second)) }
        val (declarations, relations) = nodes
            .join(Pair(agents, emptySet()))
            .join(Pair(emptySet(), downstreamRelations))
        return Package(subtopology.id(), declarations, relations)
    }

    private fun processNode(node: TopologyDescription.Node): Pair<Set<String>, Set<String>> = when (node) {
        is TopologyDescription.Source -> node.sourceToUml()
        is TopologyDescription.Processor -> node.processorToUml()
        is TopologyDescription.Sink -> node.sinkToUml()
        else -> Pair(emptySet(), emptySet())
    }

    private fun TopologyDescription.Source.sourceToUml(): Pair<Set<String>, Set<String>> =
        this.topicSet().map(PlantUML::formatPlantUmlNames)
            .map { formattedTopicName -> formatSourceToUmlRelation(formattedTopicName, this.name()) }
            .let { Pair(emptySet(), it.toSet()) }

    private fun TopologyDescription.Processor.processorToUml(): Pair<Set<String>, Set<String>> =
        this.stores().map { it to formatPlantUmlNames(it) }.map { (storeName, formattedStoreName) ->
            formatStoreToUmlDatabase(storeName, formattedStoreName) to formatStoreToUmlRelation(
                formattedStoreName, this.name()
            )
        }.split()

    private fun TopologyDescription.Sink.sinkToUml(): Pair<Set<String>, Set<String>> = this.let {
        val formattedSinkName = formatPlantUmlNames(it.topic())
        val nodeName = this.name()
        Pair(emptySet(), setOf(formatSinkToUmlRelation(formattedSinkName, nodeName)))
    }

    private fun List<Pair<String, String>>.split(): Pair<Set<String>, Set<String>> = this.let {
        val declarations = it.map { pair -> pair.first }.toSet()
        val relations = it.map { pair -> pair.second }.toSet()
        declarations to relations
    }

    private fun Pair<Set<String>, Set<String>>.join(other: Pair<Set<String>, Set<String>>): Pair<Set<String>, Set<String>> =
        this.let { Pair(it.first.union(other.first), it.second.union(other.second)) }

    private fun formatPlantUmlNames(string: String) =
        string.removeSurrounding("[", "]").replace("[^A-Za-z0-9_]".toRegex(), "_")

    private fun formatTopicToUmlQueue(topicName: String) = """
        queue "$topicName" <<topic>> as ${formatPlantUmlNames(topicName)}
    """.trimIndent()

    private fun formatSourceToUmlRelation(formattedTopicName: String, nodeName: String) = """
        $formattedTopicName --> ${formatPlantUmlNames(nodeName)}
    """.trimIndent()

    private fun formatStoreToUmlDatabase(storeName: String, formattedStoreName: String) = """
        database "$storeName" <<State Store>> as $formattedStoreName
    """.trimIndent()

    private fun formatStoreToUmlRelation(formattedStoreName: String, nodeName: String) = """
        $formattedStoreName -- ${formatPlantUmlNames(nodeName)}
    """.trimIndent()

    private fun formatSinkToUmlRelation(formattedSinkName: String, nodeName: String) = """
        $formattedSinkName <-- ${formatPlantUmlNames(nodeName)}
    """.trimIndent()

    private fun formatNodeToUmlAgent(nodeName: String, nodeType: String) = """
        agent "$nodeName" <<$nodeType>> as ${formatPlantUmlNames(nodeName)}
    """.trimIndent()

    private fun formatNodeToUmlRelation(nodeName: String, downstreamNodeName: String) = """
        ${formatPlantUmlNames(nodeName)} --> ${formatPlantUmlNames(downstreamNodeName)}
    """.trimIndent()

    private data class Package(val id: Int, val declarations: Set<String>, val relations: Set<String>) {
        override fun toString(): String =
            """
                | package "Sub-topology: $id" {
                | $TAB${declarations.joinToString(EOL + TAB)}
                | $TAB${relations.joinToString(EOL + TAB)}
                | }
            """.trimMargin("| ")
    }

    private data class UML(val packages: Set<Package>, val queue: Set<String>) {
        override fun toString(): String =
            """
                | @startuml
                | !theme black-knight
                | ${queue.joinToString(EOL)}
                | ${packages.joinToString(EOL) { it.toString() }}
                | @enduml
            """.trimMargin("| ")
    }
}

class Mermaid(
    private val topology: Topology,
) {
    fun generateDiagram(direction: Direction = Direction.LR, disableJobs: Boolean = false): String =
        createContent(topology)
            .reduce { acc, mermaidContent -> acc.accumulate(mermaidContent) }
            .also { if(disableJobs) it.disableJobs() }
            .diagram(direction)

    fun generateSubDiagrams(direction: Direction = Direction.LR): List<String> =
        createContent(topology)
            .map { mermaidContent -> mermaidContent.diagram(direction) }

    fun generateDiagramWithDatabaseSink(direction: Direction = Direction.LR, topicToDb: Map<String, String>): String {
        val content = createContent(topology)
            .reduce { acc, mermaidContent -> acc.accumulate(mermaidContent) }

        return content.diagram(direction, topicToDb)
    }

    private class MermaidContent(
        private var sourcesToSinks: List<String> = emptyList(),
        private var joins: List<String> = emptyList(),
        private var stateProcessors: List<String> = emptyList(),
        private var stores: List<String> = emptyList(),
        private var jobs: List<String> = emptyList(),
        private var jobStreams: List<String> = emptyList(),
        private var joinStreams: List<String> = emptyList(),
        private var customStateProcessorStreams: List<String> = emptyList(),
        private var toTableStreams: List<String> = emptyList(),
        private var repartitionStreams: List<String> = emptyList(),
        private var topics: Set<String> = emptySet(),
        private var branches: Set<String> = emptySet(),
    ) {
        fun disableJobs() {
            this.jobs = emptyList()
            this.jobStreams = emptyList()
        }

        fun accumulate(other: MermaidContent): MermaidContent {
            this.sourcesToSinks += other.sourcesToSinks
            this.joins += other.joins
            this.stateProcessors += other.stateProcessors
            this.stores += other.stores
            this.jobs += other.jobs
            this.jobStreams += other.jobStreams
            this.joinStreams += other.joinStreams
            this.customStateProcessorStreams += other.customStateProcessorStreams
            this.toTableStreams += other.toTableStreams
            this.repartitionStreams += other.repartitionStreams
            this.topics = topics union other.topics
            this.branches union other.branches
            return this
        }

        fun diagram(direction: Direction, topicToDb: Map<String, String> = emptyMap()): String {
            val topicStyle = topics.joinToString(EOL) { style(it, "#c233b4") }
            val storeStyle = stores.joinToString(EOL) { style(it, "#78369f") }
            val jobStyle = jobs.joinToString(EOL) { style(it, "#78369f") }

            return template(
                direction = direction,
                topics = topics.joinToString(EOL + TAB) { it.topicShape },
                joins = joins.joinToString(EOL + TAB) { it.joinShape },
                stateProcessors = stateProcessors.joinToString(EOL + TAB) { it.processorShape },
                stores = stores.joinToString(EOL + TAB) { it.storeShape },
                jobs = jobs.joinToString(EOL + TAB) { it.jobShape },
                branchStreams = branches.joinToString(EOL + TAB),
                joinStreams = joinStreams.joinToString(EOL + TAB),
                customStateProcessorStreams = customStateProcessorStreams.joinToString(EOL + TAB),
                toTableStreams = toTableStreams.joinToString(EOL + TAB),
                jobStreams = jobStreams.joinToString(EOL + TAB),
                repartitionStreams = repartitionStreams.joinToString(EOL + TAB),
                sourcesToSinks = sourcesToSinks.joinToString(EOL + TAB),
                styles = listOf(topicStyle, storeStyle, jobStyle).distinct().joinToString(EOL),
                customDbs = topicToDb.values.distinct().joinToString(EOL + TAB) { db -> db.storeShape },
                topicToDbArrows = topicToDb.toList().joinToString(EOL + TAB) { (topic, db) -> "$topic --> $db" },
            )
        }

        private val String.topicShape get() = "$this([$this])"
        private val String.joinShape get() = if (this.contains("-leftjoin-")) "$this{leftjoin}" else "$this{join}"
        private val String.processorShape get() = "$this{operation}"
        private val String.storeShape get() = "$this[($this)]"
        private val String.jobShape get() = "$this(($this))"
    }

    private fun createContent(topology: Topology): List<MermaidContent> =
        topology.describe().subtopologies().map { subtopology ->
            val nodes = subtopology.nodes()
            val sources = nodes.filterIsInstance<Source>()
            val sinks = nodes.filterIsInstance<Sink>()
            val processors = nodes.filterIsInstance<Processor>()

            val statefulProcessors = processors.filter { it.stores().isNotEmpty() }
            val statefulJobs = statefulProcessors.filter { it.successors().isEmpty() }
            val statefulStores = statefulProcessors.flatMap(Processor::stores).distinct()
            val statefulJoins = statefulProcessors.filter { it.name().contains("join-") }
            val customStateProcessorStreams = statefulProcessors.filter { it.name().contains("-operation-") }
            val jobNames = statefulJobs.map(Processor::name).distinct()

            val content = if (statefulProcessors.isEmpty()) {
                val sourcesToSinks = sources.flatMap { source ->
                    source.topicSet().flatMap { topic ->
                        findSinks(source).map { sink -> path(topic, sink.topic()) }
                    }
                }
                MermaidContent(sourcesToSinks = sourcesToSinks)
            } else {
                val statefulToTableProcessors = statefulProcessors.filter { it.name().contains("-to-table") }
                MermaidContent(
                    joins = statefulJoins.map { it.name() },
                    stores = statefulStores,
                    jobs = jobNames,
                    jobStreams = jobStreams(statefulJobs),
                    joinStreams = joinStreams(statefulJoins),
                    toTableStreams = toTableStreams(statefulToTableProcessors),
                    repartitionStreams = repartitionStreams(sources, sinks),
                )
            }

            content.accumulate(
                MermaidContent(
                    stateProcessors = customStateProcessorStreams.map { it.name() },
                    customStateProcessorStreams = statefulProcessorStreams(customStateProcessorStreams)
                )
            )

            val branchedProcessors = processors.filter { it.successors().size > 1 }
            val topicNames = (sources + sinks).flatMap(::topicNames).toSet()

            content.accumulate(
                MermaidContent(
                    topics = topicNames,
                    branches = branchStreams(branchedProcessors, statefulJoins, customStateProcessorStreams),
                )
            )
        }

    private fun toTableStreams(statefulToTableProcessors: List<Processor>): List<String> =
        statefulToTableProcessors.flatMap { processor ->
            findSources(processor).flatMap { source ->
                source.topicSet().flatMap { sourceTopic ->
                    processor.stores().map { store -> path(sourceTopic, store) }
                }
            }
        }

    private fun jobStreams(statefulProcessors: List<Processor>): List<String> =
        statefulProcessors.flatMap { processor ->
            processor.stores().map { storeName -> path(processor.name(), storeName) }
        }

    private fun branchStreams(
        branchedProcessors: List<Processor>,
        joins: List<Processor>,
        customStateProcessors: List<Processor>
    ): Set<String> = branchedProcessors
        .filterNot { it.isTraversingAny(joins + customStateProcessors) }
        .flatMap { processor ->
            findSources(processor).flatMap { source ->
                source.topicSet().flatMap { sourceTopic ->
                    findSinks(processor)
                        .map { it.topic() }
                        .map { sinkTopic -> path(sourceTopic, sinkTopic) }
                }
            }
        }.toSet()

    private fun repartitionStreams(sources: List<Source>, sinks: List<Sink>): List<String> {
        val repartitionTopicNames = sinks
            .filter { it.topic().contains("-repartition") }
            .map(Sink::topic)
            .distinct()

        return sources
            .flatMap { findSinks(it).map { sink -> it.topicSet() to sink.topic() } }
            .filter { (_, sink) -> sink in repartitionTopicNames }
            .flatMap { (sources, sink) -> sources.map { source -> path(source, sink, "rekey") } }
            .distinct()
    }

    private fun joinStreams(statefulJoins: List<Processor>): List<String> =
        statefulJoins.flatMap { stateJoin ->
            stateJoin.stores().flatMap { store ->
                findSources(stateJoin).flatMap { source ->
                    val leftSide = source.topicSet().map { sourceTopicName -> path(sourceTopicName, stateJoin.name()) }
                    val rightSide = listOf(path(store, stateJoin.name()))
                    val target = findSinks(stateJoin).map { sinkTopic -> path(stateJoin.name(), sinkTopic.topic()) }
                    leftSide union rightSide union target
                }
            }
        }.distinct()

    private fun statefulProcessorStreams(customStateProcessors: List<Processor>): List<String> {
        val streams = customStateProcessors.flatMap { processor ->
            processor.stores().flatMap { store ->
                val sources = findSources(processor)
                sources.flatMap { source ->
                    val leftSide = source.topicSet().map { sourceTopicName -> path(sourceTopicName, processor.name()) }
                    val rightSide = listOf(path(store, processor.name()))
                    val sinks = findSinks(processor)
                    val target = sinks.map { sinkTopic -> path(processor.name(), sinkTopic.topic()) }
                    leftSide union rightSide union target
                }
            }
        }

        return streams.distinct()
    }


    private fun path(from: String, to: String, label: String? = null): String = when {
        label != null -> "$from --> |$label| $to"
        else -> "$from --> $to"
    }

    private fun topicNames(node: Node) = when (node) {
        is Processor -> listOf(node.name())
        is Sink -> listOf(node.topic())
        is Source -> node.topicSet()
        else -> emptyList()
    }

    private fun findSinks(node: Node): List<Sink> =
        when (node.successors().isNotEmpty()) {
            true -> node.successors().flatMap { findSinks(it) }
            false -> listOf(node).filterIsInstance<Sink>()
        }

    private fun findSources(node: Node): List<Source> =
        when {
            node.predecessors().isNotEmpty() -> node.predecessors().flatMap { findSources(it) }
            node is Source -> listOf(node)
            else -> error("source node was not of type TopologyDescription.Source")
        }

    private fun Node.isTraversingAny(joins: List<Processor>): Boolean =
        predecessors().any { predecessor ->
            joins.any { join -> join == predecessor }
        }

    enum class Direction(private val description: String) {
        TB("top to bottom"),
        TD("top-down/ same as top to bottom"),
        BT("bottom to top"),
        RL("right to left"),
        LR("left to right");
    }
}

private fun style(name: String, hexColor: String) =
    "style $name fill:$hexColor, stroke:#2a204a, stroke-width:2px, color:#2a204a"

private fun template(
    direction: Mermaid.Direction,
    topics: String,
    joins: String,
    stateProcessors: String,
    stores: String,
    jobs: String,
    styles: String,
    joinStreams: String,
    customStateProcessorStreams: String,
    branchStreams: String,
    toTableStreams: String,
    jobStreams: String,
    repartitionStreams: String,
    sourcesToSinks: String,
    customDbs: String,
    topicToDbArrows: String,
) = """
%%{init: {'theme': 'dark', 'themeVariables': { 'primaryColor': '#07cff6', 'textColor': '#dad9e0', 'lineColor': '#07cff6'}}}%%

graph ${direction.name}

subgraph Topologi
    %% TOPICS
    $topics

    %% JOINS
    $joins

    %% STATE PROCESSORS
    $stateProcessors
    
    %% STATE STORES
    $stores
    
    %% DATABASES
    $customDbs
    $topicToDbArrows

    %% PROCESSOR API JOBS
    $jobs
    
    %% JOIN STREAMS
    $joinStreams

    %% TABLE STREAMS
    $toTableStreams

    %% JOB STREAMS
    $jobStreams
    
    %% BRANCH STREAMS
    $branchStreams

    %% REPARTITION STREAMS
    $repartitionStreams
    
    %% BASIC STREAMS
    $sourcesToSinks
    
    %% CUSTOM PROCESS STREAMS
    $customStateProcessorStreams
end

%% COLORS
%% light    #dad9e0
%% purple   #78369f
%% pink     #c233b4
%% dark     #2a204a
%% blue     #07cff6

%% STYLES
$styles
"""

private const val EOL = "\n"
private const val TAB = "\t"

