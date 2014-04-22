package ads1.ss14.can;

import ads1.ss14.can.exceptions.CANException;
import ads1.ss14.can.exceptions.NoAdditionalStorageAvailable;
import ads1.ss14.can.exceptions.NoSuchDocument;

import java.util.ArrayList;
import java.util.HashMap;

public class Client implements ClientInterface, ClientCommandInterface{

    private String uniqueID;
    private int networkXSize,networkYSize,maxNumberOfDocuments;
    private HashMap<String,Pair<Document,Position>> documents;
    private ArrayList<ClientInterface> neighbours;
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
        this.neighbours = new ArrayList<ClientInterface>();
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
        ClientInterface clientToRemove = null;

        for (ClientInterface client : getNeighbours()) {
            if (client.getUniqueID().equals(clientID)) {
                neighbours.remove(clientToRemove);
                return;
            }
        }
    }

    public ClientInterface getNearestNeighbour(Position p) {

        ClientInterface nearestNeighbour = null;
        double distance = 0;

        for (ClientInterface client : getNeighbours()) {
            Area area = client.getArea();
            int x = (int)Math.max(Math.min(area.getUpperX(), p.getX()), area.getLowerX());
            int y = (int)Math.max(Math.min(area.getUpperY(), p.getY()), area.getLowerY());
            double q =  Math.sqrt(Math.pow(x - p.getX(),2) + Math.pow(y - p.getY(),2));

            if (distance == 0 || q < distance) {
                distance = q;
                nearestNeighbour = client;

            } else if (q == distance && client.getUniqueID().compareTo(nearestNeighbour.getUniqueID()) < 0) {
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

    @Override
    public void adaptNeighbours(ClientInterface joiningClient) {

        boolean horizontalSplit = this.getArea().getLowerX() == joiningClient.getArea().getLowerX();

        Area joiningArea = joiningClient.getArea();
        double joiningLower = horizontalSplit ? joiningArea.getLowerY() : joiningArea.getLowerX();
        double joiningUpper = horizontalSplit ? joiningArea.getUpperY() : joiningArea.getUpperX();

        ArrayList<ClientInterface> neighboursCopy = new ArrayList<ClientInterface>(neighbours);

        for (ClientInterface neighbour : neighboursCopy) {
            Area neighbourArea = neighbour.getArea();

            double neighbourLower = horizontalSplit ? neighbourArea.getLowerY() : neighbourArea.getLowerX();
            double neighbourUpper = horizontalSplit ? neighbourArea.getUpperY() : neighbourArea.getUpperX();

            if (horizontalSplit && neighbourUpper == joiningLower ||
                    !horizontalSplit && neighbourLower == joiningUpper) {
                neighbour.removeNeighbour(this.uniqueID);
                neighbour.addNeighbour(joiningClient);
                this.removeNeighbour(neighbour.getUniqueID());
                joiningClient.addNeighbour(neighbour);
            } else if (neighbourLower <= joiningLower && joiningLower < neighbourUpper ||
                       neighbourLower < joiningUpper && joiningUpper <= neighbourUpper) {
                joiningClient.addNeighbour(neighbour);
                neighbour.addNeighbour(joiningClient);
            } else if (!(neighbourLower <= joiningLower && joiningLower < neighbourUpper ||
                         neighbourLower < joiningUpper && joiningUpper <= neighbourUpper)) {
                this.removeNeighbour(neighbour.getUniqueID());
                neighbour.removeNeighbour(this.uniqueID);
            }
        }

        this.addNeighbour(joiningClient);
        joiningClient.addNeighbour(this);
    }

    @Override
    public Iterable<Pair<Document, Position>> removeUnmanagedDocuments() {
        ArrayList<Pair<Document, Position>> unmanagedDocuments = new ArrayList<Pair<Document, Position>>();

        for (Pair<Document,Position> pair : documents.values()) {
            if (!this.area.contains(pair.second)){
                unmanagedDocuments.add(pair);
                // don't know if this will work in the same loop ..
                documents.remove(pair.first.getName());
            }
        }

        return unmanagedDocuments;
    }


    @Override
    public void addDocumentToNetwork(Document d) throws CANException {

        for (int i = 0; i <10; i++) {
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

        for (int i = 0; i <10; i++) {
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

        for (int i = 0; i <10; i++) {
            Position p = getPosition(documentName,i);
            ClientInterface client = searchForResponsibleClient(p);

            try {
                return client.getDocument(documentName);
            } catch (NoSuchDocument ex) { }
        }

        return null;
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