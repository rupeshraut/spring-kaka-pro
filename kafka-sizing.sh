#!/bin/bash

# Kafka Property Sizing Calculator Script
# This script helps you quickly calculate optimal Kafka properties for your use case

echo "🔧 Kafka Property Sizing Calculator"
echo "=================================="
echo ""

# Default values
MESSAGES_PER_SECOND=1000
MESSAGE_SIZE=1024
PROCESSING_TIME_MS=10
CONSUMER_COUNT=3
LOW_LATENCY=false
HIGH_DURABILITY=true

# Check if interactive mode
if [ "$1" = "-i" ] || [ "$1" = "--interactive" ]; then
    echo "📝 Interactive Mode - Please provide your requirements:"
    echo ""
    
    read -p "Messages per second (default: $MESSAGES_PER_SECOND): " input
    MESSAGES_PER_SECOND=${input:-$MESSAGES_PER_SECOND}
    
    read -p "Average message size in bytes (default: $MESSAGE_SIZE): " input
    MESSAGE_SIZE=${input:-$MESSAGE_SIZE}
    
    read -p "Processing time per message in ms (default: $PROCESSING_TIME_MS): " input
    PROCESSING_TIME_MS=${input:-$PROCESSING_TIME_MS}
    
    read -p "Number of consumers (default: $CONSUMER_COUNT): " input
    CONSUMER_COUNT=${input:-$CONSUMER_COUNT}
    
    read -p "Require low latency? (true/false, default: $LOW_LATENCY): " input
    LOW_LATENCY=${input:-$LOW_LATENCY}
    
    read -p "Require high durability? (true/false, default: $HIGH_DURABILITY): " input
    HIGH_DURABILITY=${input:-$HIGH_DURABILITY}
    
    echo ""
fi

# Check for command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --messages-per-second)
            MESSAGES_PER_SECOND="$2"
            shift 2
            ;;
        --message-size)
            MESSAGE_SIZE="$2"
            shift 2
            ;;
        --processing-time)
            PROCESSING_TIME_MS="$2"
            shift 2
            ;;
        --consumer-count)
            CONSUMER_COUNT="$2"
            shift 2
            ;;
        --low-latency)
            LOW_LATENCY="$2"
            shift 2
            ;;
        --high-durability)
            HIGH_DURABILITY="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -i, --interactive         Run in interactive mode"
            echo "  --messages-per-second     Expected messages per second (default: 1000)"
            echo "  --message-size           Average message size in bytes (default: 1024)"
            echo "  --processing-time        Processing time per message in ms (default: 10)"
            echo "  --consumer-count         Number of consumers (default: 3)"
            echo "  --low-latency            Require low latency (true/false, default: false)"
            echo "  --high-durability        Require high durability (true/false, default: true)"
            echo "  -h, --help               Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 --messages-per-second 5000 --low-latency true"
            echo "  $0 -i"
            echo "  $0 --message-size 2048 --high-durability false"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

echo "🚀 Running Kafka sizing calculator with:"
echo "   Messages per second: $MESSAGES_PER_SECOND"
echo "   Message size: $MESSAGE_SIZE bytes"
echo "   Processing time: $PROCESSING_TIME_MS ms"
echo "   Consumer count: $CONSUMER_COUNT"
echo "   Low latency: $LOW_LATENCY"
echo "   High durability: $HIGH_DURABILITY"
echo ""

# Set environment variables
export KAFKA_SIZING_MESSAGES_PER_SECOND=$MESSAGES_PER_SECOND
export KAFKA_SIZING_MESSAGE_SIZE=$MESSAGE_SIZE
export KAFKA_SIZING_PROCESSING_TIME_MS=$PROCESSING_TIME_MS
export KAFKA_SIZING_CONSUMER_COUNT=$CONSUMER_COUNT
export KAFKA_SIZING_LOW_LATENCY=$LOW_LATENCY
export KAFKA_SIZING_HIGH_DURABILITY=$HIGH_DURABILITY

# Run the Spring Boot application with sizing calculator enabled
echo "⏳ Calculating optimal Kafka properties..."
echo ""

./gradlew bootRun --args="--kafka.sizing.enabled=true"

echo ""
echo "✅ Sizing calculation complete!"
echo ""
echo "💡 Pro Tips:"
echo "   - These are starting recommendations based on your requirements"
echo "   - Always test with your actual data patterns and load"
echo "   - Monitor metrics and adjust based on performance"
echo "   - Consider your hardware and network constraints"
echo "   - Review the complete sizing guide in KAFKA_SIZING_GUIDE.md"
