#!/zsh

API_URL="http://localhost:8080/"
DB_FILE="./script_test.udb"

echo "starting test"

json_payload=$(jq -n \
  --arg load_cmd "load $DB_FILE" \
  --arg create_cmd "create-table users id:number username:text email:text" \
  --arg insert1 "insert into users belgarion user1@example.com" \
  --arg insert2 "insert into users polgara user2@example.com" \
  --arg insert3 "insert into users silk user3@example.com" \
  --arg select_all "select all rows from users;" \
  --arg select_limit "select 2 rows from users;" \
  --arg select_filter "select all rows from users where id>1;" \
  --arg update_cmd "update users set email=belgarion@example.com where id=1;" \
  --arg delete_cmd "delete from users where id=3;" \
  --arg select_after "select all rows from users;" \
  '{
    requests: [
      $load_cmd,
      $create_cmd,
      $insert1,
      $insert2,
      $insert3,
      $select_all,
      $select_limit,
      $select_filter,
      $update_cmd,
      $delete_cmd,
      $select_after
    ]
  }')

echo "sending request:"
echo "$json_payload" | jq .

response=$(curl -s -X POST "$API_URL" \
     -H "Content-Type: application/json" \
     -d "$json_payload")

echo "response:"
echo "$response" | jq . 2>/dev/null || echo "Raw Response: $response"

echo ""
echo "test complete"