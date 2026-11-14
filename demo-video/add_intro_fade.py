#!/usr/bin/env python3
"""Apply the v7 intro transition to the already assembled 89-second v6 film."""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent

def run(*args):
    subprocess.run(args, check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=ROOT / 'lumi-demo-v6.mp4')
    parser.add_argument('--output', type=Path, default=ROOT / 'lumi-demo-v7.mp4')
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error('Use a different output path to preserve the source.')
    frames = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-skip_frame', 'nokey', '-select_streams', 'v:0',
        '-show_frames', '-show_entries', 'frame=pts_time', '-of', 'json', str(args.input)
    ]))['frames']
    # Preserve the existing frames after the first suitable keyframe. All cue
    # times and the three-second opening remain unchanged.
    cut = round(next(float(f['pts_time']) for f in frames if float(f['pts_time']) > 3.8) * 30) / 30
    end = f'{cut:.9f}'
    with tempfile.TemporaryDirectory(prefix='lumi-intro-fade-') as folder:
        folder = Path(folder)
        head, tail = folder / 'head.mp4', folder / 'tail.mp4'
        graph = (
            '[0:v]split=2[a][b];'
            '[a]trim=start=0:end=3,setpts=PTS-STARTPTS,fade=t=out:st=2.45:d=0.55[intro];'
            f'[b]trim=start=3:end={end},setpts=PTS-STARTPTS,fade=t=in:st=0:d=0.8[scene];'
            '[intro][scene]concat=n=2:v=1:a=0[v]'
        )
        run('ffmpeg', '-hide_banner', '-loglevel', 'warning', '-y', '-t', end,
            '-i', str(args.input), '-filter_complex', graph, '-map', '[v]', '-an',
            '-r', '30', '-c:v', 'libx264', '-preset', 'fast', '-crf', '18',
            '-pix_fmt', 'yuv420p', '-color_primaries', 'bt709', '-color_trc', 'bt709',
            '-colorspace', 'bt709', '-movflags', '+faststart', str(head))
        run('ffmpeg', '-hide_banner', '-loglevel', 'warning', '-y', '-ss', end,
            '-i', str(args.input), '-map', '0:v:0', '-c', 'copy', '-an', str(tail))
        listing = folder / 'concat.txt'
        listing.write_text(f"file '{head}'\nfile '{tail}'\n")
        run('ffmpeg', '-hide_banner', '-loglevel', 'warning', '-y', '-f', 'concat',
            '-safe', '0', '-i', str(listing), '-c', 'copy', '-movflags', '+faststart', str(args.output))
    print(f'Saved {args.output}; intro fades out at 2.45–3.00s and footage fades in at 3.00–3.80s.')

if __name__ == '__main__':
    main()
