package io.kyso;

import org.bson.Document;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoDbClient {

    private MongoClient mongoClient;
    private MongoDatabase database;

    public MongoDbClient(String uri) {
        MongoClientURI mongoClientURI = new MongoClientURI(uri);
        this.mongoClient = new MongoClient(mongoClientURI);
        this.database = mongoClient.getDatabase("kyso");
    }

    public void closeConnection() {
        this.mongoClient.close();
    }

    public FindIterable<Document> find(String collectionName, BasicDBObject searchQuery) {
        MongoCollection<Document> collection = this.database.getCollection(collectionName);
        return collection.find(searchQuery);
    }

    public Organization getOrganizationByOrganizationSlug(String organizationSlug) {
        BasicDBObject organizationSearchQuery = new BasicDBObject();
        organizationSearchQuery.put("sluglified_name", organizationSlug);
        FindIterable<Document> cursorOrganizations = this.find("Organization", organizationSearchQuery);
        Document organizationDocument = cursorOrganizations.first();
        if (organizationDocument != null) {
            return new Organization(organizationDocument);
        } else {
            return null;
        }
    }

    public Team getTeamByOrganizationIdAndTeamSlug(String organizationId, String teamSlug) {
        BasicDBObject teamSearchQuery = new BasicDBObject();
        teamSearchQuery.put("organization_id", organizationId);
        teamSearchQuery.put("sluglified_name", teamSlug);
        FindIterable<Document> cursorTeams = this.find("Team", teamSearchQuery);
        Document teamDocument = cursorTeams.first();
        if (teamDocument != null) {
            Team team = new Team(teamDocument);
            return team;
        } else {
            return null;
        }
    }

}
