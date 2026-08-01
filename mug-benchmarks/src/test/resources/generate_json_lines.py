import json
import random
import string

# Output path
OUTPUT_PATH = '/Users/benyu/mug/mug-benchmarks/src/test/resources/large_benchmark.jsonl'

# Character sets
LOREM_CHARS = string.ascii_letters + " "
ESCAPE_CHARS = ['\\n', '\\t', '\\"', '\\\\', '\\/']

def random_string(min_len=20, max_len=120):
    length = random.randint(min_len, max_len)
    chars = []
    for _ in range(length):
        # 4% chance of inserting an escape sequence
        if random.random() < 0.04:
            chars.append(random.choice(ESCAPE_CHARS))
        elif random.random() < 0.005:
            chars.append(chr(random.randint(0x80, 0x9FF)))
        else:
            chars.append(random.choice(LOREM_CHARS))
    return "".join(chars)

def random_number():
    num_type = random.choice(['int', 'float', 'scientific'])
    if num_type == 'int':
        return random.randint(0, 10000000)
    elif num_type == 'float':
        return round(random.uniform(-10000.0, 10000.0), 4)
    else:  # scientific
        sign = "-" if random.random() < 0.5 else ""
        base = round(random.uniform(1.0, 9.9), 2)
        exp_sign = random.choice(['+', '-'])
        exp = random.randint(1, 15)
        return float(f"{sign}{base}e{exp_sign}{exp}")

def random_primitive():
    prim_type = random.choice(['string', 'number', 'bool', 'null'])
    if prim_type == 'string':
        return random_string()
    elif prim_type == 'number':
        return random_number()
    elif prim_type == 'bool':
        return random.choice([True, False])
    else:
        return None

def random_value(depth=0):
    # Enforce maximum depth of 5
    if depth >= 5:
        return random_primitive()
    
    val_type = random.choice(['object', 'array', 'primitive'])
    if val_type == 'object':
        return random_object(depth + 1)
    elif val_type == 'array':
        return random_array(depth + 1)
    else:
        return random_primitive()

def random_array(depth):
    # Target ~1KB average record size
    size = random.randint(2, 4)
    return [random_value(depth) for _ in range(size)]

def random_object(depth):
    # Target ~1KB average record size
    size = random.randint(2, 4)
    obj = {}
    for _ in range(size):
        key = random_string(min_len=5, max_len=12)
        obj[key] = random_value(depth)
    return obj

def generate_jsonl(count=10000):
    # Distribution: 80% objects, 15% arrays, 5% primitives
    types = (['object'] * int(count * 0.80) +
             ['array'] * int(count * 0.15) +
             ['primitive'] * int(count * 0.05))
    
    random.shuffle(types)
    
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        for t in types:
            if t == 'object':
                record = random_object(depth=1)
            elif t == 'array':
                record = random_array(depth=1)
            else:
                record = random_primitive()
            
            f.write(json.dumps(record, ensure_ascii=True) + '\n')

if __name__ == '__main__':
    random.seed(42)  # For deterministic generation
    generate_jsonl(10000)
    print(f"Successfully generated 10,000 JSON Lines records at {OUTPUT_PATH}")
