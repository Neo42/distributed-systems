# Restful Weather Aggregation

## How to run

1. cd into the project directory
2. run `mvn compile` to compile the project
3. run `mvn test` to test the project
4. run `mvn exec:java -Dexec.mainClass="com.example.AggregationServer"` to start the server
5. run `mvn exec:java -Dexec.mainClass="com.example.ContentServer"` to start the content server
6. run `mvn exec:java -Dexec.mainClass="com.example.Client"` to start the client

## Functionalities

### Basic Functionality

- ✅ **Text sending works**:

  - Verified by tests like `testValidGetRequestWithoutStationId` and `testPutRequest`.

- ✅ **Client, server, and content server processes start up and communicate**:

  - Verified by tests like `testServerStartup` and `testPutRequest`.

- ✅ **PUT operation works for one content server**:

  - Verified by tests like `testPutRequest` and `testSubsequentPutRequests`.

- ✅ **GET operation works for many read clients**:

  - Verified by tests like `testGetRequestAfterPut` and `testConcurrentRequests`.

- ✅ **Aggregation server expunging expired data works (30s)**:

  - Verified by `testDataExpirationAfterTimeout`.

- ✅ **Retry on errors (server not available etc) works**:
  - Verified by `testFailureTolerance` and `testLamportClockIncrementOnRetry`.

### Full Functionality

- ✅ **Lamport clocks are implemented**:

  - Verified by tests like `testLamportClockUpdateOnReceive`, `testLamportClockIncrementOnPut`, and `testLamportClockSynchronization`.

- ✅ **All error codes are implemented**:

  - Verified by tests like `testNotFoundResponse`, `testErrorResponse`, `testInvalidRequestMethod`, and `testInvalidRequestFormat`.

- ✅ **Content servers are replicated and fault tolerant**:
  - Verified by `testFaultToleranceWithMultipleContentServers`.

### Bonus Functionality (10 points)

- ✅ **JSON parsing using your own code**:
  - Verified by `JsonParserTest` methods like `testFromJson_FullyFormattedData`.

### Summary

- **Basic Functionality**: All basic functionalities are implemented and verified by the corresponding tests.
- **Full Functionality**: All full functionalities are implemented and verified by the corresponding tests.
- **Bonus Functionality**: JSON parsing using custom code is implemented and verified by the corresponding tests.
