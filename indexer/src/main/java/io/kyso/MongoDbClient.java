package io.kyso;

import java.util.ArrayList;

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
        try {
            this.mongoClient.close();
        } catch (Exception ex) {
            System.out.println("Error closing MongoDB connection");
            ex.printStackTrace();
        }
    }

    public FindIterable<Document> find(String collectionName, BasicDBObject searchQuery) {
        MongoCollection<Document> collection = this.database.getCollection(collectionName);
        return collection.find(searchQuery);
    }

    public int countDocuments(String collectionName, BasicDBObject searchQuery) {
        MongoCollection<Document> collection = this.database.getCollection(collectionName);
        return (int) collection.countDocuments(searchQuery);
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

    public Report getReportByTeamIdAndReportSlug(String teamId, String reportSlug) {
        BasicDBObject reportSearchQuery = new BasicDBObject();
        reportSearchQuery.put("team_id", teamId);
        reportSearchQuery.put("sluglified_name", reportSlug);
        FindIterable<Document> cursorReports = this.find("Report", reportSearchQuery);
        Document reportDocument = cursorReports.first();
        if (reportDocument != null) {
            Report report = new Report(reportDocument);
            return report;
        } else {
            return null;
        }
    }

    public User getUserByUserId(String userId) {
        BasicDBObject userSearchQuery = new BasicDBObject();
        userSearchQuery.put("id", userId);
        FindIterable<Document> cursorUsers = this.find("User", userSearchQuery);
        Document userDocument = cursorUsers.first();
        if (userDocument != null) {
            User user = new User(userDocument);
            return user;
        } else {
            return null;
        }
    }

    public ArrayList<Tag> getTagsGivenEntityId(String entityId) {
        ArrayList<TagAssign> tagAssignments = new ArrayList<TagAssign>();
        BasicDBObject tagAssignSearchQuery = new BasicDBObject();
        tagAssignSearchQuery.put("entity_id", entityId);
        FindIterable<Document> cursorTagAssignments = this.find("TagAssign", tagAssignSearchQuery);
        for (Document tagAssignDocument : cursorTagAssignments) {
            TagAssign tagAssign = new TagAssign(tagAssignDocument);
            tagAssignments.add(tagAssign);
        }
        ArrayList<Tag> tags = new ArrayList<Tag>();
        BasicDBObject tagSearchQuery = new BasicDBObject();
        tagSearchQuery.put("id", new BasicDBObject("$in", tagAssignments.stream().map(ta -> ta.getTagId()).toArray()));
        FindIterable<Document> cursorTags = this.find("Tag", tagSearchQuery);
        for (Document tagDocument : cursorTags) {
            Tag tag = new Tag(tagDocument);
            tags.add(tag);
        }
        return tags;
    }

    public int getNumberOfStarsGivenReportId(String reportId) {
        BasicDBObject starSearchQuery = new BasicDBObject();
        starSearchQuery.put("report_id", reportId);
        return this.countDocuments("StarredReport", starSearchQuery);
    }

    public int getNumberOfCommentsGivenReportId(String reportId) {
        BasicDBObject commentSearchQuery = new BasicDBObject();
        commentSearchQuery.put("report_id", reportId);
        return this.countDocuments("Comment", commentSearchQuery);
    }

}
