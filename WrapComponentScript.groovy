import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.settings.SlingSettingsService

def queryManager = session.workspace.queryManager
def sqlQuery = "SELECT * FROM [nt:unstructured] AS comp WHERE ISDESCENDANTNODE(comp, '/content/my-project') AND NAME() = 'inner'"
def modifiedPages = 0
def replicatedPages = 0

if (isAuthorInstance()) {
    queryManager.createQuery(sqlQuery, 'sql').execute().nodes.each { innerNode ->
        def parentNode = innerNode.getParent()
        def nodePath = innerNode.getPath()

        try{
        if ("inner-wrapper".equals(innerNode.getParent().getName())) {
            println "Node at path: ${innerNode.path} already has 'inner-wrapper'. Skipping..."
        } else {
            def wrapperNode = parentNode.addNode("inner-wrapper", "nt:unstructured")
            wrapperNode.setProperty("sling:resourceType", "my-project/components/wrapper")
            //wrapperNode.orderBefore(wrapperNode.getPath(),innerNode.getPath())

            Node newNode = wrapperNode.addNode(innerNode.name, innerNode.primaryNodeType.name)
            newNode.setProperty("sling:resourceType", innerNode.getProperty("sling:resourceType").getString())
            innerNode.remove()

            replicatePage(wrapperNode, newNode)
            session.save()
            modifiedPages++
            replicatedPages++
            println "Node at path = $nodePath has been modified and replicated."
        }
     } catch(Exception e) {
        println "Execption occurred while doing the wrapping operation: " +e
     }
    }

    if (modifiedPages == 0) {
        println "No node needs modification or replication!!"
    }
} else {
    println "Script should only run on the author instance. Exiting..."
}

def replicatePage(Node wrapperNode, Node newNode) {
    try {
        if (!wrapperNode.hasProperty("cq:lastReplicated") && !newNode.hasProperty("cq:lastReplicated")) {
            def replicator = getService("com.day.cq.replication.Replicator")
            replicator.replicate(session, ReplicationActionType.ACTIVATE, wrapperNode.getPath());
            replicator.replicate(session, ReplicationActionType.ACTIVATE, newNode.getPath());
        } else {
            println "Page at path = ${wrapperNode.path} has been modified after replication. Not replicating again."
        }
    } catch (Exception e) {
        log.error("Error replicating page: ${wrapperNode.path}", e)
    }
}

def isAuthorInstance() {
    def slingSettingsService = getService("org.apache.sling.settings.SlingSettingsService")
    return slingSettingsService.runModes.contains("author")
}
