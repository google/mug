import json
import random
import string
import os

# Ensure the output directory exists
os.makedirs('/Users/benyu/mug/mug-benchmarks/src/test/resources', exist_ok=True)

# Counters to track the number of containers generated
map_count = 0
list_count = 0
target_containers = 100

def random_plain_string(min_len=20, max_len=128):
    length = random.randint(min_len, max_len)
    chars = string.ascii_letters + string.digits + "    "
    body = "".join(random.choice(chars) for _ in range(length - 4))
    return f"str_{body}_end"

def random_string(min_len=20, max_len=128):
    length = random.randint(min_len, max_len)
    chars = string.ascii_letters + string.digits + "    "
    body_list = [random.choice(chars) for _ in range(length - 4)]
    
    # Decide if we want to inject escapes
    r = random.random()
    if r < 0.005:  # 0.5% unicode escape (non-ASCII char)
        non_ascii_char = chr(random.choice([0x263a, 0x2705, 0x00A0, 0x00B0, 0x00C9]))
        idx = random.randint(0, len(body_list) - 1)
        body_list[idx] = non_ascii_char
    elif r < 0.045:  # 4.0% standard escapes (0.005 to 0.045)
        escape_char = random.choice(['\n', '\t', '\r', '\b', '\f', '"', '\\'])
        idx = random.randint(0, len(body_list) - 1)
        body_list[idx] = escape_char

    body = "".join(body_list)
    return f"str_{body}_end"

def random_number():
    choice = random.choice(['pos_int', 'neg_int', 'float', 'sci_pos', 'sci_neg'])
    if choice == 'pos_int':
        return random.randint(1, 1000000)
    elif choice == 'neg_int':
        return random.randint(-1000000, -1)
    elif choice == 'float':
        return round(random.uniform(-10000.0, 10000.0), 4)
    elif choice == 'sci_pos':
        # e.g., 1.23e+123
        base = round(random.uniform(1.0, 9.9), 2)
        exponent = random.randint(100, 150)
        # Return as a float value in the JSON object (or string representation if needed, but JSON numbers support scientific notation natively)
        return float(f"{base}e+{exponent}")
    elif choice == 'sci_neg':
        # e.g., -4.56e-123
        base = round(random.uniform(-9.9, -1.0), 2)
        exponent = random.randint(100, 150)
        return float(f"{base}e-{exponent}")

def random_primitive():
    choice = random.choice(['null', 'true', 'false', 'number', 'string'])
    if choice == 'null':
        return None
    elif choice == 'true':
        return True
    elif choice == 'false':
        return False
    elif choice == 'number':
        return random_number()
    elif choice == 'string':
        return random_string()

def generate_map(depth):
    global map_count, list_count
    map_count += 1
    
    obj = {}
    for i in range(12):
        key = f"key_{i}_{random_plain_string(10, 20)}"
        
        # Decide whether to nest a container
        total_containers = map_count + list_count
        if total_containers < target_containers and depth < 5 and random.random() < 0.4:
            # Nest a container
            if random.random() < 0.5:
                obj[key] = generate_map(depth + 1)
            else:
                obj[key] = generate_list(depth + 1)
        else:
            obj[key] = random_primitive()
            
    return obj

def generate_list(depth):
    global map_count, list_count
    list_count += 1
    
    arr = []
    # We need a list of size 250
    for _ in range(250):
        total_containers = map_count + list_count
        # To keep total containers around 100, only a few elements in a list should be nested containers
        if total_containers < target_containers and depth < 5 and random.random() < 0.02:
            if random.random() < 0.5:
                arr.append(generate_map(depth + 1))
            else:
                arr.append(generate_list(depth + 1))
        else:
            arr.append(random_primitive())
            
    return arr

# Generate the top-level container as a map of size 12
root = generate_map(depth=0)

# Write to the destination file
output_path = '/Users/benyu/mug/mug-benchmarks/src/test/resources/large_benchmark.json'
with open(output_path, 'w') as f:
    json.dump(root, f, indent=2)

print(f"Successfully generated JSON with {map_count} maps and {list_count} lists (Total: {map_count + list_count} containers).")
print(f"Output saved to: {output_path}")
