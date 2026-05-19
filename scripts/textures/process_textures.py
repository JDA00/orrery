#!/usr/bin/env python3
"""
Planetary Texture Processing Pipeline for LWJGL Orrery
Converts large planetary textures (TIF/PNG) to BC7 compressed DDS format
using the installed KTX-Software toktx tool.
"""

import subprocess
import shutil
from pathlib import Path
import argparse
import json
import time
from typing import Dict, List, Optional, Tuple
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
import os
import tempfile

class PlanetaryTextureProcessor:
    """Process large planetary textures for use in LWJGL Orrery."""
    
    # Standard resolutions for game engine use
    RESOLUTIONS = {
        '16k': (16384, 8192),
        '8k': (8192, 4096),
        '4k': (4096, 2048),
        '2k': (2048, 1024),
        '1k': (1024, 512),
        '512': (512, 256)
    }
    
    # Format handlers
    SUPPORTED_FORMATS = {'.tif', '.tiff', '.png', '.jpg', '.jpeg', '.psd'}
    
    def __init__(self, output_dir: Path = None, log_level: str = 'INFO'):
        """Initialize the processor."""
        self.output_dir = output_dir or Path('textures_processed')
        self.output_dir.mkdir(exist_ok=True)
        
        # Setup logging
        logging.basicConfig(
            level=getattr(logging, log_level),
            format='%(asctime)s - %(levelname)s - %(message)s'
        )
        self.logger = logging.getLogger(__name__)
        
        # Check for required tools
        self._check_dependencies()
        
    def _check_dependencies(self) -> None:
        """Verify required tools are installed."""
        required_tools = {
            'toktx': 'KTX-Software for BC7 compression',
            'convert': 'ImageMagick for image manipulation'
        }
        
        for tool, description in required_tools.items():
            if not shutil.which(tool):
                self.logger.warning(f"{tool} not found: {description}")
                if tool == 'toktx':
                    raise RuntimeError("toktx is required. Install KTX-Software.")
    
    def _detect_planet_name(self, filepath: Path) -> str:
        """Detect planet name from filename."""
        filename = filepath.stem.lower()
        
        # Common planet name patterns
        planets = ['mercury', 'venus', 'earth', 'mars', 'jupiter', 
                  'saturn', 'uranus', 'neptune', 'pluto', 'moon']
        
        for planet in planets:
            if planet in filename:
                return planet
        
        # Special cases
        if 'luna' in filename:
            return 'moon'
        if 'sol' in filename or 'sun' in filename:
            return 'sun'
        
        # Extract first word as fallback
        return filename.split('_')[0].split('-')[0]
    
    def _resize_image(self, input_path: Path, output_path: Path, 
                     resolution: Tuple[int, int]) -> bool:
        """Resize image using ImageMagick."""
        width, height = resolution
        
        cmd = [
            'convert',
            str(input_path),
            '-resize', f'{width}x{height}!',  # Force exact size
            '-depth', '8',
            str(output_path)
        ]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True)
            if result.returncode != 0:
                self.logger.error(f"ImageMagick resize failed: {result.stderr}")
                return False
            return True
        except Exception as e:
            self.logger.error(f"Resize failed: {e}")
            return False
    
    def _convert_to_png(self, input_path: Path, output_path: Path) -> bool:
        """Convert any format to PNG for processing."""
        if input_path.suffix.lower() == '.png':
            # Already PNG, just copy
            if input_path != output_path:
                shutil.copy2(input_path, output_path)
            return True
        
        cmd = [
            'convert',
            str(input_path),
            '-depth', '8',
            str(output_path)
        ]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True)
            return result.returncode == 0
        except Exception as e:
            self.logger.error(f"PNG conversion failed: {e}")
            return False
    
    def compress_to_bc7_dds(self, input_png: Path, output_dds: Path) -> bool:
        """Compress PNG to BC7 DDS using toktx."""
        cmd = [
            'toktx',
            '--bcmp',  # Use BC compression
            '--format', 'BC7_RGBA',  # BC7 format
            '--mipmap',  # Generate mipmaps
            str(output_dds),
            str(input_png)
        ]
        
        try:
            self.logger.info(f"Compressing to BC7: {output_dds.name}")
            result = subprocess.run(cmd, capture_output=True, text=True)
            
            if result.returncode != 0:
                self.logger.error(f"BC7 compression failed: {result.stderr}")
                return False
            
            # Verify output exists
            if not output_dds.exists():
                self.logger.error(f"Output file not created: {output_dds}")
                return False
            
            # Log compression ratio
            input_size = input_png.stat().st_size
            output_size = output_dds.stat().st_size
            ratio = (1 - output_size / input_size) * 100
            self.logger.info(f"Compressed {input_png.name}: {input_size/(1024*1024):.1f}MB -> {output_size/(1024*1024):.1f}MB ({ratio:.1f}% reduction)")
            
            return True
            
        except Exception as e:
            self.logger.error(f"Compression failed: {e}")
            return False
    
    def process_texture(self, input_path: Path, 
                       resolutions: Optional[List[str]] = None,
                       output_format: str = 'dds',
                       parallel: bool = True) -> Dict[str, Path]:
        """
        Process a single texture to multiple resolutions.
        
        Args:
            input_path: Path to input texture
            resolutions: List of resolution keys (e.g., ['8k', '4k', '2k'])
            output_format: Output format ('dds' or 'ktx2')
            parallel: Use parallel processing for multiple resolutions
        
        Returns:
            Dictionary mapping resolution to output path
        """
        if not input_path.exists():
            raise FileNotFoundError(f"Input file not found: {input_path}")
        
        # Default resolutions
        if resolutions is None:
            # Auto-select based on file size
            file_size_mb = input_path.stat().st_size / (1024 * 1024)
            if file_size_mb > 1000:  # > 1GB
                resolutions = ['8k', '4k', '2k']
            elif file_size_mb > 100:  # > 100MB
                resolutions = ['4k', '2k', '1k']
            else:
                resolutions = ['2k', '1k', '512']
        
        # Detect planet name
        planet = self._detect_planet_name(input_path)
        self.logger.info(f"Processing {planet} texture: {input_path.name}")
        
        # Create planet output directory
        planet_dir = self.output_dir / planet
        planet_dir.mkdir(exist_ok=True)
        
        # Results dictionary
        results = {}
        
        # Use temp directory for intermediate files
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            
            # Convert to PNG if needed
            if input_path.suffix.lower() != '.png':
                self.logger.info(f"Converting {input_path.suffix} to PNG...")
                master_png = temp_path / f"{planet}_master.png"
                if not self._convert_to_png(input_path, master_png):
                    self.logger.error("Failed to convert to PNG")
                    return results
            else:
                master_png = input_path
            
            # Process each resolution
            tasks = []
            if parallel and len(resolutions) > 1:
                with ThreadPoolExecutor(max_workers=4) as executor:
                    for res_key in resolutions:
                        if res_key not in self.RESOLUTIONS:
                            self.logger.warning(f"Unknown resolution: {res_key}")
                            continue
                        
                        future = executor.submit(
                            self._process_single_resolution,
                            master_png, planet, res_key, planet_dir, temp_path, output_format
                        )
                        tasks.append((res_key, future))
                    
                    # Collect results
                    for res_key, future in tasks:
                        try:
                            result = future.result()
                            if result:
                                results[res_key] = result
                        except Exception as e:
                            self.logger.error(f"Failed to process {res_key}: {e}")
            else:
                # Sequential processing
                for res_key in resolutions:
                    if res_key not in self.RESOLUTIONS:
                        self.logger.warning(f"Unknown resolution: {res_key}")
                        continue
                    
                    result = self._process_single_resolution(
                        master_png, planet, res_key, planet_dir, temp_path, output_format
                    )
                    if result:
                        results[res_key] = result
        
        return results
    
    def _process_single_resolution(self, master_png: Path, planet: str, 
                                  res_key: str, planet_dir: Path, 
                                  temp_path: Path, output_format: str) -> Optional[Path]:
        """Process a single resolution."""
        resolution = self.RESOLUTIONS[res_key]
        width, height = resolution
        
        self.logger.info(f"Processing {planet} at {res_key} ({width}x{height})")
        
        # Resize PNG
        resized_png = temp_path / f"{planet}_{res_key}.png"
        if not self._resize_image(master_png, resized_png, resolution):
            self.logger.error(f"Failed to resize to {res_key}")
            return None
        
        # Compress to DDS/KTX2
        if output_format == 'dds':
            output_file = planet_dir / f"{planet}_visual_{res_key}.dds"
        else:
            output_file = planet_dir / f"{planet}_visual_{res_key}.ktx2"
        
        if self.compress_to_bc7_dds(resized_png, output_file):
            self.logger.info(f"Created: {output_file}")
            return output_file
        
        return None
    
    def batch_process(self, input_dir: Path, pattern: str = "*",
                     resolutions: Optional[List[str]] = None) -> None:
        """
        Process all matching textures in a directory.
        
        Args:
            input_dir: Directory containing source textures
            pattern: Glob pattern for file matching
            resolutions: List of resolutions to generate
        """
        input_path = Path(input_dir)
        if not input_path.exists():
            raise FileNotFoundError(f"Input directory not found: {input_dir}")
        
        # Find all matching files
        files = []
        for ext in self.SUPPORTED_FORMATS:
            files.extend(input_path.glob(f"{pattern}{ext}"))
            files.extend(input_path.glob(f"{pattern}{ext.upper()}"))
        
        if not files:
            self.logger.warning(f"No files found matching pattern: {pattern}")
            return
        
        self.logger.info(f"Found {len(files)} files to process")
        
        # Process each file
        for filepath in files:
            try:
                results = self.process_texture(filepath, resolutions)
                if results:
                    self.logger.info(f"Successfully processed {filepath.name}: {list(results.keys())}")
                else:
                    self.logger.warning(f"No outputs generated for {filepath.name}")
            except Exception as e:
                self.logger.error(f"Failed to process {filepath}: {e}")
    
    def create_metadata(self, output_dir: Optional[Path] = None) -> None:
        """Create metadata JSON for all processed textures."""
        target_dir = output_dir or self.output_dir
        
        metadata = {
            'version': '1.0',
            'created': time.strftime('%Y-%m-%d %H:%M:%S'),
            'planets': {}
        }
        
        # Scan for DDS files
        for planet_dir in target_dir.iterdir():
            if not planet_dir.is_dir():
                continue
            
            planet_name = planet_dir.name
            textures = []
            
            for dds_file in planet_dir.glob('*.dds'):
                # Parse filename
                parts = dds_file.stem.split('_')
                if len(parts) >= 3:
                    resolution = parts[-1]
                    layer = parts[-2] if len(parts) > 2 else 'visual'
                    
                    textures.append({
                        'file': dds_file.name,
                        'resolution': resolution,
                        'layer': layer,
                        'size_mb': round(dds_file.stat().st_size / (1024*1024), 2)
                    })
            
            if textures:
                metadata['planets'][planet_name] = {
                    'textures': textures,
                    'count': len(textures)
                }
        
        # Write metadata
        metadata_path = target_dir / 'texture_metadata.json'
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2)
        
        self.logger.info(f"Created metadata: {metadata_path}")


def main():
    parser = argparse.ArgumentParser(description='Process planetary textures for LWJGL Orrery')
    parser.add_argument('input', type=Path, help='Input file or directory')
    parser.add_argument('-o', '--output', type=Path, default='textures_processed',
                       help='Output directory (default: textures_processed)')
    parser.add_argument('-r', '--resolutions', nargs='+', 
                       choices=['16k', '8k', '4k', '2k', '1k', '512'],
                       help='Target resolutions (default: auto-detect)')
    parser.add_argument('-f', '--format', choices=['dds', 'ktx2'], default='dds',
                       help='Output format (default: dds)')
    parser.add_argument('-b', '--batch', action='store_true',
                       help='Batch process directory')
    parser.add_argument('-p', '--pattern', default='*',
                       help='File pattern for batch mode (default: *)')
    parser.add_argument('--no-parallel', action='store_true',
                       help='Disable parallel processing')
    parser.add_argument('-m', '--metadata', action='store_true',
                       help='Generate metadata JSON after processing')
    parser.add_argument('-v', '--verbose', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    # Initialize processor
    log_level = 'DEBUG' if args.verbose else 'INFO'
    processor = PlanetaryTextureProcessor(args.output, log_level)
    
    # Process
    if args.batch:
        processor.batch_process(args.input, args.pattern, args.resolutions)
    else:
        if not args.input.is_file():
            print(f"Error: {args.input} is not a file. Use --batch for directories.")
            return 1
        
        results = processor.process_texture(
            args.input, 
            args.resolutions,
            args.format,
            not args.no_parallel
        )
        
        if results:
            print(f"\nSuccessfully created {len(results)} textures:")
            for res, path in results.items():
                size_mb = path.stat().st_size / (1024*1024)
                print(f"  {res}: {path.name} ({size_mb:.1f} MB)")
    
    # Generate metadata if requested
    if args.metadata:
        processor.create_metadata()
    
    return 0


if __name__ == '__main__':
    exit(main())