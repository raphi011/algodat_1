package ads1.ss14.can;

import ads1.ss14.can.exceptions.CANException;
import ads1.ss14.can.exceptions.NoAdditionalStorageAvailable;
import ads1.ss14.can.exceptions.NoSuchDocument;

import java.util.HashMap;
import java.util.LinkedList;

public class Client implements ClientInterface, ClientCommandInterface{

    private String uniqueID;
    private int networkXSize,networkYSize,maxNumberOfDocuments;
    private HashMap<String,Pair<Document,Position>> documents;
    private LinkedList<ClientInterface> neighbours;
    private Area area;

    /**
     * Constructs a new Client
     * @param uniqueID the ID of the Client in the CAN
     * @param networkXSize the size along the horizontal axis in the CAN
     * @param networkYSize the size along the vertical axis in the CAN
     */
    public Client(String uniqueID, int networkXSize, int networkYSize) {
        this.uniqueID = uniqueID;
        this.networkXSize = networkXSize;
        this.networkYSize = networkYSize;

        this.documents = new HashMap<String, Pair<Document,Position>>();
        this.neighbours = new LinkedList<ClientInterface>();
    }

    public int getSize() {
        return networkXSize * networkYSize;
    }

    @Override
    public String getUniqueID() {
        return uniqueID;
    }

    @Override
    public void setMaxNumberOfDocuments(int m) {
        this.maxNumberOfDocuments = m;
    }

    @Override
    public int getMaxNumberOfDocuments() {
        return this.maxNumberOfDocuments;
    }

    @Override
    public Document getDocument(String documentName) throws NoSuchDocument {
        if (!documents.containsKey(documentName))
            throw new NoSuchDocument();
        return documents.get(documentName).first;
    }

    @Override
    public void storeDocument(Document d, Position p) throws NoAdditionalStorageAvailable, CANException {
        if (documents.size() >= maxNumberOfDocuments)
            throw new NoAdditionalStorageAvailable();

        documents.put(d.getName(),new Pair<Document,Position>(d,p));
    }

    @Override
    public void deleteDocument(String documentName) throws NoSuchDocument {
        if (documents.containsKey(documentName))
            documents.remove(documentName);
        else
            throw new NoSuchDocument();
    }

    @Override
    public Position getPosition() {
        Area area = getArea();
        double x = area.getLowerX() + ((area.getUpperX() - area.getLowerX()) / 2);
        double y = area.getLowerY() + ((area.getUpperY() - area.getLowerY()) / 2);

        return new Position(x,y);
    }

    @Override
    public Area getArea() {
        return area;
    }

    @Override
    public void setArea(Area newArea) {
        this.area = newArea;
    }

    @Override
    public Iterable<ClientInterface> getNeighbours() {
        return this.neighbours;
    }

    @Override
    public void addNeighbour(ClientInterface newNeighbour){
        neighbours.add(newNeighbour);
    }

    @Override
    public void removeNeighbour(String clientID) {
        LinkedList<ClientInterface> neighboursCopy = new LinkedList<ClientInterface>(neighbours);

        for (ClientInterface client : neighboursCopy) {
            if (client.getUniqueID().equals(clientID)) {
                neighbours.remove(client);
                return;
            }
        }
    }

    public ClientInterface getNearestNeighbour(Position p) {

        ClientInterface nearestNeighbour = null;
        double distance = -1;

        for (ClientInterface client : getNeighbours()) {
            Area area = client.getArea();
            double x = Math.max(Math.min(area.getUpperX(), p.getX()), area.getLowerX());
            double y = Math.max(Math.min(area.getUpperY(), p.getY()), area.getLowerY());
            double q = Math.sqrt(Math.pow(x - p.getX(),2) + Math.pow(y - p.getY(),2));

            if (distance == -1 || q < distance) {
                distance = q;
                nearestNeighbour = client;

            } else if (q == distance && client.getUniqueID().compareTo(nearestNeighbour.getUniqueID()) > 0) {
                nearestNeighbour = client;
            }
        }

        return nearestNeighbour;
    }

    private Position getPosition(String documentName, int i) {
        return new Position(hX(documentName, i), hY(documentName, i));
    }

    @Override
    public ClientInterface searchForResponsibleClient(Position p) {
        if (this.getArea().contains(p))
            return this;

        for (ClientInterface client : getNeighbours()) {
            if (client.getArea().contains(p))
                return client;
        }

        ClientInterface client = getNearestNeighbour(p);

        return client.searchForResponsibleClient(p);
    }

    @Override
    public ClientInterface joinNetwork(ClientInterface entryPoint, Position p) throws CANException {
        if (entryPoint == null) {
            this.area = new Area(0, networkXSize, 0, networkYSize);
            return null;
        }
        else {
            ClientInterface client = entryPoint.searchForResponsibleClient(p);
            Area area = client.getArea();
            // Area too small, can't join
            if ((area.getUpperX() - area.getLowerX()) < 1 || (area.getUpperY() - area.getLowerY()) < 1)
                return null;
            else if (Math.abs(area.getLowerX() - area.getUpperX()) >= Math.abs(area.getLowerY() - area.getUpperY())) {
                Pair<Area,Area> splitAreas = area.splitVertically();

                client.setArea(splitAreas.first);
                this.setArea(splitAreas.second);
            }
            else /* Math.abs(area.getLowerX() - area.getUpperX()) < Math.abs(area.getLowerY() - area.getUpperY()) */ {
                Pair<Area,Area> splitAreas = area.splitHorizontally();

                client.setArea(splitAreas.second);
                this.setArea(splitAreas.first);
            }

            client.adaptNeighbours(this);

            for (Pair<Document,Position> doc : client.removeUnmanagedDocuments())
                documents.put(doc.first.getName(),new Pair<Document,Position>(doc.first,doc.second));

            return client;
        }
    }

    public void adaptNeighbours(ClientInterface joiningClient) {

        LinkedList<ClientInterface> neighboursCopy = new LinkedList<ClientInterface>(neighbours);
        boolean verticalSplit = this.getArea().getLowerY() == joiningClient.getArea().getLowerY();
        Area joiningArea = joiningClient.getArea();

        for (ClientInterface neighbour : neighboursCopy) {
            Area neighbourArea = neighbour.getArea();

            if (isLowerOrRightNeighbour(neighbourArea,joiningArea,verticalSplit)) {

                this.removeNeighbour(neighbour.getUniqueID());
                neighbour.removeNeighbour(this.uniqueID);
                joiningClient.addNeighbour(neighbour);
                neighbour.addNeighbour(joiningClient);
            } else if (isHorizontalOrVertical(neighbourArea, joiningArea, verticalSplit)) {


                if (isHorizontalOrVerticalNeighbour(neighbourArea,joiningArea,verticalSplit)) {
                    joiningClient.addNeighbour(neighbour);
                    neighbour.addNeighbour(joiningClient);
                } if (!isHorizontalOrVerticalNeighbour(neighbourArea,this.getArea(),verticalSplit)) {
                    this.removeNeighbour(neighbour.getUniqueID());
                    neighbour.removeNeighbour(this.uniqueID);
                }
            }
        }

        this.addNeighbour(joiningClient);
        joiningClient.addNeighbour(this);
    }

    private boolean isLowerOrRightNeighbour(Area neighbourArea, Area joiningArea, boolean verticalSplit) {

        if (verticalSplit) return neighbourArea.getLowerX() == joiningArea.getUpperX();
        else return neighbourArea.getUpperY() == joiningArea.getLowerY();
    }

    private boolean isHorizontalOrVertical(Area neighbourArea, Area joiningArea, boolean verticalSplit) {
        if (verticalSplit)
            return neighbourArea.getLowerY() == joiningArea.getUpperY() ||
                   neighbourArea.getUpperY() == joiningArea.getLowerY();
        else
            return neighbourArea.getLowerX() == joiningArea.getUpperX() ||
                   neighbourArea.getUpperX() == joiningArea.getLowerX();
    }

    private boolean isHorizontalOrVerticalNeighbour(Area neighbourArea, Area joiningArea, boolean verticalSplit) {
        if (verticalSplit)
            return (neighbourArea.getLowerX() <= joiningArea.getLowerX() && joiningArea.getLowerX() < neighbourArea.getUpperX()) ||
                    (neighbourArea.getLowerX() < joiningArea.getUpperX() && joiningArea.getUpperX() <= neighbourArea.getUpperX());
        else
            return (neighbourArea.getLowerY() <= joiningArea.getLowerY() && joiningArea.getLowerY() < neighbourArea.getUpperY()) ||
                    (neighbourArea.getLowerY() < joiningArea.getUpperY() && joiningArea.getUpperY() <= neighbourArea.getUpperY());
    }

    @Override
    public Iterable<Pair<Document, Position>> removeUnmanagedDocuments() {
        LinkedList<Pair<Document, Position>> unmanagedDocuments = new LinkedList<Pair<Document, Position>>();

        for (Pair<Document,Position> pair : documents.values())
            if (!this.area.contains(pair.second))
                unmanagedDocuments.add(pair);

        for (Pair<Document,Position> doc : unmanagedDocuments)
            documents.remove(doc.first.getName());


        return unmanagedDocuments;
    }


    @Override
    public void addDocumentToNetwork(Document d) throws CANException {

        for (int i = 0; i <getSize(); i++) {
            Position p = getPosition(d.getName(),i);
            ClientInterface client = searchForResponsibleClient(p);

            try {
                client.storeDocument(d, p);
                return;
            } catch (NoAdditionalStorageAvailable ex) { }
        }
    }

    @Override
    public void removeDocumentFromNetwork(String documentName) {

        for (int i = 0; i <getSize(); i++) {
            Position p = getPosition(documentName,i);
            ClientInterface client = searchForResponsibleClient(p);

            try {
                client.deleteDocument(documentName);
                return;
            } catch (NoSuchDocument ex) { }
        }
    }

    @Override
    public Document searchForDocument(String documentName) throws CANException {
        Document doc = null;

        for (int i = 0; i <getSize(); i++) {
            Position p = getPosition(documentName,i);
            ClientInterface client = searchForResponsibleClient(p);

            try {
                doc = client.getDocument(documentName);
                break;
            } catch (NoSuchDocument ex) { }
        }

        return doc;
    }

    @Override
    public String toString() {
        return this.getUniqueID();
    }

    private int hash(String name, int i) {

        int size = networkXSize * networkYSize;
        int ordSum = ordSum(name);
        int rh = i * ((2*(ordSum % (size - 2))) + 1);

        int h = ((ordSum % size) + rh) % size;
        return h;
    }

    private int hX(String name, int i) {
        return hash(name,i) % networkXSize;
    }

    private int hY(String name, int i) {
        return hash(name,i) / networkXSize;
    }

    private int ordSum(String name) {
        int sum = 0;

        for (char c : name.toLowerCase().toCharArray())
            sum += c - 'a';

        return sum;
    }
}