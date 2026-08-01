import random
import string

input_path = '/Users/benyu/mug/mug-benchmarks/src/test/resources/large_benchmark.json'
output_path = '/Users/benyu/mug/mug-benchmarks/src/test/resources/large_benchmark_with_comments.json'

with open(input_path, 'r') as f:
    lines = f.readlines()

comment_chars = string.ascii_letters + string.digits + " " * 5  # More spaces for natural text look

output_lines = []
for line in lines:
    stripped = line.strip()
    
    # 1. 10% probability of a line comment appended at the end of the line
    if stripped and random.random() < 0.10:
        comment_len = random.randint(15, 45)  # Average 30 chars
        comment_text = "".join(random.choices(comment_chars, k=comment_len))
        line = line.rstrip('\n') + f" // {comment_text}\n"
    
    # 2. 25% probability of a block comment placed on its own line before the JSON line
    if random.random() < 0.25:
        comment_len = random.randint(100, 300)  # Average 200 chars
        comment_text = "".join(random.choices(comment_chars, k=comment_len))
        indent = len(line) - len(line.lstrip())
        indent_str = line[:indent]
        output_lines.append(f"{indent_str}/* {comment_text} */\n")
        
    output_lines.append(line)

with open(output_path, 'w') as f:
    f.writelines(output_lines)

print("Generated large_benchmark_with_comments.json successfully!")
