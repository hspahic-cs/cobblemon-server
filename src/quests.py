import json

# Load existing quests data
def load_quests_data():
    with open('data/quests.json', 'r') as file:
        return json.load(file)

# Save updated quests data
def save_quests_data(quests):
    with open('data/quests.json', 'w') as file:
        json.dump(quests, file, indent=4)

# Update the 10k wallet quest to reward a key
def update_10k_wallet_quest():
    quests = load_quests_data()
    for quest in quests['quests']:
        if quest['id'] == '10k_wallet':
            quest['reward'] = {'type': 'key', 'name': 'Special Key'}
    save_quests_data(quests)

# Call the function to update the quest
update_10k_wallet_quest()
