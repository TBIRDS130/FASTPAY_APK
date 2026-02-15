#!/bin/bash

# FASTPAY Script Testing Suite - All-in-One Command
# Usage: ./test_scripts.sh [OPTION]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Header
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}    FASTPAY SCRIPT TESTING SUITE       ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to show help
show_help() {
    echo -e "${YELLOW}USAGE:${NC}"
    echo "  ./test_scripts.sh [OPTION]"
    echo ""
    echo -e "${YELLOW}OPTIONS:${NC}"
    echo "  all           Run ALL script tests (default)"
    echo "  quick         Run quick tests only"
    echo "  commands      Test script commands only"
    echo "  init          Test script initialization only"
    echo "  integration   Test integration only"
    echo "  coverage      Run tests with coverage report"
    echo "  specific      Run specific test method"
    echo "  manual        Manual in-app testing guide"
    echo "  help          Show this help message"
    echo ""
    echo -e "${YELLOW}EXAMPLES:${NC}"
    echo "  ./test_scripts.sh                    # Run all tests"
    echo "  ./test_scripts.sh quick              # Quick tests only"
    echo "  ./test_scripts.sh coverage           # With coverage report"
    echo "  ./test_scripts.sh specific           # Interactive test selection"
    echo ""
}

# Function to run all script tests
run_all_tests() {
    echo -e "${GREEN}🚀 Running ALL Script Tests...${NC}"
    echo ""
    
    ./gradlew test --tests "com.example.fast.script.*"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ ALL SCRIPT TESTS PASSED!${NC}"
    else
        echo -e "${RED}❌ SOME TESTS FAILED!${NC}"
        exit 1
    fi
}

# Function to run quick tests
run_quick_tests() {
    echo -e "${GREEN}⚡ Running Quick Script Tests...${NC}"
    echo ""
    
    ./gradlew test --tests "com.example.fast.script.ScriptTestCommandsTest.getActiveScripts"
    ./gradlew test --tests "com.example.fast.script.ScriptTestCommandsTest.testMessageProcessing"
    ./gradlew test --tests "com.example.fast.script.ScriptInitializerTest.initialize"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ QUICK TESTS PASSED!${NC}"
    else
        echo -e "${RED}❌ QUICK TESTS FAILED!${NC}"
        exit 1
    fi
}

# Function to test script commands only
test_script_commands() {
    echo -e "${GREEN}📝 Testing Script Commands...${NC}"
    echo ""
    
    ./gradlew test --tests "com.example.fast.script.ScriptTestCommandsTest"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ SCRIPT COMMAND TESTS PASSED!${NC}"
    else
        echo -e "${RED}❌ SCRIPT COMMAND TESTS FAILED!${NC}"
        exit 1
    fi
}

# Function to test script initialization only
test_script_init() {
    echo -e "${GREEN}🔧 Testing Script Initialization...${NC}"
    echo ""
    
    ./gradlew test --tests "com.example.fast.script.ScriptInitializerTest"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ SCRIPT INITIALIZATION TESTS PASSED!${NC}"
    else
        echo -e "${RED}❌ SCRIPT INITIALIZATION TESTS FAILED!${NC}"
        exit 1
    fi
}

# Function to test integration only
test_integration() {
    echo -e "${GREEN}🔗 Testing Script Integration...${NC}"
    echo ""
    
    ./gradlew test --tests "com.example.fast.service.ScriptCommandsIntegrationTest"
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ INTEGRATION TESTS PASSED!${NC}"
    else
        echo -e "${RED}❌ INTEGRATION TESTS FAILED!${NC}"
        exit 1
    fi
}

# Function to run tests with coverage
run_with_coverage() {
    echo -e "${GREEN}📊 Running Tests with Coverage Report...${NC}"
    echo ""
    
    ./gradlew test --tests "com.example.fast.script.*" jacocoTestReport
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ TESTS WITH COVERAGE COMPLETED!${NC}"
        echo -e "${YELLOW}📈 Coverage report available at: build/reports/jacoco/test/html/index.html${NC}"
    else
        echo -e "${RED}❌ COVERAGE TESTS FAILED!${NC}"
        exit 1
    fi
}

# Function for specific test selection
run_specific_test() {
    echo -e "${GREEN}🎯 Specific Test Selection${NC}"
    echo ""
    echo -e "${YELLOW}Available Test Methods:${NC}"
    echo "1. getActiveScripts"
    echo "2. testMessageProcessing"
    echo "3. testCommandValidation"
    echo "4. loadAxisScript"
    echo "5. getScriptStatistics"
    echo "6. forceSyncScripts"
    echo "7. initialize (ScriptInitializer)"
    echo "8. integration tests"
    echo ""
    read -p "Enter test number (1-8): " choice
    
    case $choice in
        1)
            echo -e "${GREEN}Running getActiveScripts test...${NC}"
            ./gradlew test --tests "*getActiveScripts*"
            ;;
        2)
            echo -e "${GREEN}Running testMessageProcessing test...${NC}"
            ./gradlew test --tests "*testMessageProcessing*"
            ;;
        3)
            echo -e "${GREEN}Running testCommandValidation test...${NC}"
            ./gradlew test --tests "*testCommandValidation*"
            ;;
        4)
            echo -e "${GREEN}Running loadAxisScript test...${NC}"
            ./gradlew test --tests "*loadAxisScript*"
            ;;
        5)
            echo -e "${GREEN}Running getScriptStatistics test...${NC}"
            ./gradlew test --tests "*getScriptStatistics*"
            ;;
        6)
            echo -e "${GREEN}Running forceSyncScripts test...${NC}"
            ./gradlew test --tests "*forceSyncScripts*"
            ;;
        7)
            echo -e "${GREEN}Running ScriptInitializer tests...${NC}"
            ./gradlew test --tests "*ScriptInitializer*"
            ;;
        8)
            echo -e "${GREEN}Running integration tests...${NC}"
            ./gradlew test --tests "*ScriptCommandsIntegration*"
            ;;
        *)
            echo -e "${RED}❌ Invalid choice!${NC}"
            exit 1
            ;;
    esac
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ SPECIFIC TEST PASSED!${NC}"
    else
        echo -e "${RED}❌ SPECIFIC TEST FAILED!${NC}"
        exit 1
    fi
}

# Function to show manual testing guide
show_manual_testing() {
    echo -e "${GREEN}📱 Manual In-App Testing Guide${NC}"
    echo ""
    echo -e "${YELLOW}Send these commands via remote interface:${NC}"
    echo ""
    echo -e "${BLUE}1. Get Active Scripts:${NC}"
    echo "   Command: getActiveScripts"
    echo "   Expected: Returns list of active scripts"
    echo ""
    echo -e "${BLUE}2. Test Message Processing:${NC}"
    echo "   Command: testMessageScript|+1234567890|Test message"
    echo "   Expected: Message processed through script system"
    echo ""
    echo -e "${BLUE}3. Test Command Validation:${NC}"
    echo "   Command: testCommandScript|sendSms|phone=+1234567890&message=Test&sim=1"
    echo "   Expected: Command validated through script system"
    echo ""
    echo -e "${BLUE}4. Load AXIS Script:${NC}"
    echo "   Command: loadAxisScript"
    echo "   Expected: AXIS banking script loaded"
    echo ""
    echo -e "${BLUE}5. Get Script Statistics:${NC}"
    echo "   Command: getScriptStats"
    echo "   Expected: Returns script execution statistics"
    echo ""
    echo -e "${BLUE}6. Force Script Sync:${NC}"
    echo "   Command: syncScripts"
    echo "   Expected: Scripts synchronized from server"
    echo ""
    echo -e "${YELLOW}💡 Tips:${NC}"
    echo "- Check logs for detailed execution information"
    echo "- Monitor Firebase for tracking events"
    echo "- Verify command history updates"
}

# Main execution logic
case "${1:-all}" in
    "all")
        run_all_tests
        ;;
    "quick")
        run_quick_tests
        ;;
    "commands")
        test_script_commands
        ;;
    "init")
        test_script_init
        ;;
    "integration")
        test_integration
        ;;
    "coverage")
        run_with_coverage
        ;;
    "specific")
        run_specific_test
        ;;
    "manual")
        show_manual_testing
        ;;
    "help"|"-h"|"--help")
        show_help
        ;;
    *)
        echo -e "${RED}❌ Unknown option: $1${NC}"
        echo ""
        show_help
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}🎉 Script Testing Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
