#!/usr/bin/env python3
"""Compare two compiled core jars in alternating, isolated JVM processes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--baseline-classpath', type=Path, required=True)
parser.add_argument('--candidate-classpath', type=Path, required=True)
parser.add_argument('--java-home', type=Path, default=Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
parser.add_argument('--forks', type=int, default=4, help='Processes per implementation')
parser.add_argument('--cases', help='Optional comma-separated case names')
parser.add_argument('--harness', type=Path, default=Path(__file__).with_name('JsonAccessBenchmark.java'),
                    help='Standalone Java harness using the same JSON sample format')
parser.add_argument('--baseline-ref', help='Git revision of the baseline jar')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
classpaths = {name: path.read_text().strip() for name, path in (
    ('baseline', args.baseline_classpath), ('candidate', args.candidate_classpath))}
java = str(args.java_home / 'bin/java')
result = {
    'platform': platform.platform(),
    'java': subprocess.check_output([java, '-version'], stderr=subprocess.STDOUT, text=True).strip(),
    'jvmArgs': ['-Xms256m', '-Xmx256m', '-XX:+UseSerialGC'],
    'warmupSecondsPerCase': 1,
    'samplesPerCase': 7,
    'targetSampleMillis': 200,
    'baselineCommit': args.baseline_ref,
    'sha256': {
        name + 'Jar': hashlib.sha256(Path(cp.split(os.pathsep)[0]).read_bytes()).hexdigest()
        for name, cp in classpaths.items()
    },
    'runs': [],
}
result['harness'] = args.harness.name
result['sha256']['harness'] = hashlib.sha256(args.harness.read_bytes()).hexdigest()
with tempfile.TemporaryDirectory(prefix='harmonic-json-benchmark-') as directory:
    subprocess.run([str(args.java_home / 'bin/javac'), '-cp', classpaths['baseline'],
                    '-d', directory, str(args.harness)], check=True)
    for fork in range(args.forks):
        order = ('baseline', 'candidate') if fork % 2 == 0 else ('candidate', 'baseline')
        for version in order:
            command = [java, *result['jvmArgs'], '-cp', directory + os.pathsep + classpaths[version],
                       args.harness.stem, str(fork)]
            if args.cases:
                command.append(args.cases)
            samples = []
            with subprocess.Popen(command, stdout=subprocess.PIPE, text=True) as process:
                for line in process.stdout:
                    sample = json.loads(line)
                    samples.append(sample)
                    print(f"{version} fork {fork + 1}: {sample['case']} "
                          f"{statistics.median(sample['nsPerOp']):.1f} ns/op", flush=True)
                if process.wait() != 0:
                    raise RuntimeError(f'{version} benchmark failed')
            result['runs'].append({'version': version, 'fork': fork + 1, 'samples': samples})
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(result, indent=2) + '\n')
for case in result['runs'][0]['samples']:
    name = case['case']
    medians = {}
    for version in classpaths:
        medians[version] = statistics.median(
            statistics.median(sample['nsPerOp'])
            for run in result['runs'] if run['version'] == version
            for sample in run['samples'] if sample['case'] == name)
    print(f"{name}: {medians['baseline']:.1f} -> {medians['candidate']:.1f} ns/op "
          f"({100 * (1 - medians['candidate'] / medians['baseline']):+.1f}% faster)")
