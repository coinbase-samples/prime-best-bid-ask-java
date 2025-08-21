# Prime Best Bid / Ask Java

A reference Java application that connects to the Coinbase Prime WebSocket API to stream real-time level 2 market data and display the best bid and ask prices for specified cryptocurrency trading pairs.

## Features

- **Real-time WebSocket streaming** from Coinbase Prime
- **Automatic reconnection** with exponential backoff
- **Production-ready logging** with SLF4J and Logback
- **Graceful shutdown handling**

## Prerequisites

### System Requirements

- **Java 11** or higher
- **Maven 3.6+** for building

### Coinbase Prime API Credentials

You need the following credentials from your Coinbase Prime account:

- `API_KEY` - Your API access key
- `SECRET_KEY` - Your API secret key  
- `PASSPHRASE` - Your API passphrase
- `SVC_ACCOUNTID` - Your service account ID

## Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/coinbase-samples/prime-best-bid-ask-java
cd prime-best-bid-ask-java
```

### 2. Configure Environment Variables

Set environment variables:

```bash
export API_KEY="your_api_key_here"
export SECRET_KEY="your_secret_key_here"
export PASSPHRASE="your_passphrase_here"
export SVC_ACCOUNTID="your_account_id_here"
```

### 3. Customize Product Subscription

Edit the product IDs in `src/main/java/com/coinbase/prime/BestBidAskPrinter.java`:

```java
private static final String[] PRODUCT_IDS = {"BTC-USD", "ETH-USD"};
```

**Note**: Coinbase Prime supports up to 10 products per WebSocket connection.

## Building & Running

### Local Development

```bash
# Build the application
mvn clean package

# Run the application
java -jar target/prime-best-bid-ask-java-0.1.0.jar
```

### Using Maven directly

```bash
mvn clean compile exec:java -Dexec.mainClass="com.coinbase.prime.BestBidAskPrinter"
```

## Sample Output

```
12:24:15.123 - WebSocket connected to wss://ws-feed.prime.coinbase.com
12:24:15.456 - BTC-USD → Best Bid: 43250.12000000 (qty 0.500000) | Best Ask: 43251.50000000 (qty 0.750000)
12:24:15.789 - ETH-USD → Best Bid: 2456.78000000 (qty 2.100000) | Best Ask: 2457.20000000 (qty 1.800000)
```

## Configuration

### Reconnection Settings

Customize reconnection behavior in `BestBidAskPrinter.java`:

```java
private static final int MAX_RECONNECT_ATTEMPTS = 10;
private static final long INITIAL_RECONNECT_DELAY = 1000; // 1 second
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
